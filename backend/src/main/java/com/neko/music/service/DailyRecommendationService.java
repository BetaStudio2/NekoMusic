package com.neko.music.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neko.music.database.DatabaseManager;
import com.neko.music.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DailyRecommendationService {
    private static final Logger logger = LoggerFactory.getLogger(DailyRecommendationService.class);
    private static final ZoneId CN_ZONE = ZoneId.of("Asia/Shanghai");

    private final DatabaseManager databaseManager;
    private final RedisService redisService;
    private final ConfigManager configManager;
    private final ObjectMapper objectMapper;

    public DailyRecommendationService(DatabaseManager databaseManager,
                                      RedisService redisService,
                                      ConfigManager configManager,
                                      ObjectMapper objectMapper) {
        this.databaseManager = databaseManager;
        this.redisService = redisService;
        this.configManager = configManager;
        this.objectMapper = objectMapper;
    }

    public void regenerateForAllUsers(LocalDate recDate) {
        List<Integer> userIds = listAllUserIds();
        int ok = 0;
        for (Integer userId : userIds) {
            try {
                regenerateForUser(userId, recDate, false);
                ok++;
            } catch (Exception e) {
                logger.error("生成每日推荐失败 userId={} date={}", userId, recDate, e);
            }
        }
        logger.info("每日推荐任务完成 date={} totalUsers={} success={}", recDate, userIds.size(), ok);
    }

    public List<Map<String, Object>> getOrBuildTodayRecommendations(int userId) {
        LocalDate today = LocalDate.now(CN_ZONE);
        List<Map<String, Object>> existing = loadRecommendationsFromRedis(userId, today);
        if (!existing.isEmpty()) {
            return existing;
        }
        regenerateForUser(userId, today, true);
        return loadRecommendationsFromRedis(userId, today);
    }

    public void regenerateForUser(int userId, LocalDate recDate, boolean force) {
        if (!force && hasRecommendationsInRedis(userId, recDate)) {
            return;
        }

        Set<Integer> favoriteIds = loadUserFavoriteIds(userId);
        Set<Integer> ownPlaylistMusicIds = loadOwnPlaylistMusicIds(userId);
        Set<Integer> favoritePlaylistMusicIds = loadFavoritePlaylistMusicIds(userId);
        int candidateLimit = configManager.getRecommendationAiDailyLimit() * 8;
        List<SongCandidate> candidates = loadCandidates(userId, favoriteIds, candidateLimit);
        if (candidates.isEmpty()) {
            cacheRecommendations(userId, recDate, List.of(), List.of());
            return;
        }

        UserProfile profile = loadUserProfile(userId);
        List<RecommendationItem> ranked = rankByRule(candidates, profile, ownPlaylistMusicIds, favoritePlaylistMusicIds);
        ranked = applyAiRerankIfEnabled(userId, profile, ranked, candidates);
        ranked = deprioritizePlaylistMusic(ranked, ownPlaylistMusicIds, favoritePlaylistMusicIds);
        ranked = strictFilterFavorites(ranked, favoriteIds);

        int limit = configManager.getRecommendationAiDailyLimit();
        if (ranked.size() > limit) {
            ranked = new ArrayList<>(ranked.subList(0, limit));
        }
        cacheRecommendations(userId, recDate, ranked, candidates);
        logger.info("每日推荐已写入Redis userId={} date={} count={}", userId, recDate, ranked.size());
    }

    private List<Integer> listAllUserIds() {
        List<Integer> ids = new ArrayList<>();
        String sql = "SELECT id FROM users";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getInt("id"));
            }
        } catch (Exception e) {
            logger.error("查询用户列表失败", e);
        }
        return ids;
    }

    private boolean hasRecommendationsInRedis(int userId, LocalDate date) {
        String key = redisKey(userId, date);
        if (!redisService.exists(key)) {
            return false;
        }
        String value = redisService.get(key);
        return value != null && !value.isBlank() && !"[]".equals(value.trim());
    }

    /** 已收藏曲目：硬排除，不出现在推荐列表。 */
    private Set<Integer> loadUserFavoriteIds(int userId) {
        Set<Integer> ids = new HashSet<>();
        String sql = "SELECT music_id FROM user_favorites WHERE user_id=?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("music_id"));
                }
            }
        } catch (Exception e) {
            logger.error("查询用户收藏失败 userId={}", userId, e);
        }
        return ids;
    }

    /** 用户自建歌单内曲目：降权，仍可能进入推荐。 */
    private Set<Integer> loadOwnPlaylistMusicIds(int userId) {
        Set<Integer> ids = new HashSet<>();
        String sql = """
                SELECT DISTINCT pm.music_id
                FROM playlist_music pm
                INNER JOIN playlists p ON p.id = pm.playlist_id
                WHERE p.user_id = ?
                """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("music_id"));
                }
            }
        } catch (Exception e) {
            logger.error("查询用户歌单曲目失败 userId={}", userId, e);
        }
        return ids;
    }

    /** 用户收藏歌单内曲目：降权（弱于自建歌单），仍可能进入推荐。 */
    private Set<Integer> loadFavoritePlaylistMusicIds(int userId) {
        Set<Integer> ids = new HashSet<>();
        String sql = """
                SELECT DISTINCT pm.music_id
                FROM playlist_music pm
                INNER JOIN user_favorite_playlists ufp ON ufp.playlist_id = pm.playlist_id
                WHERE ufp.user_id = ?
                """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("music_id"));
                }
            }
        } catch (Exception e) {
            logger.error("查询收藏歌单曲目失败 userId={}", userId, e);
        }
        return ids;
    }

    private UserProfile loadUserProfile(int userId) {
        Map<String, Integer> artistCount = new HashMap<>();
        Map<String, Integer> langCount = new HashMap<>();
        Set<String> tagSet = new LinkedHashSet<>();

        String sql = """
                SELECT m.artist, m.language, m.tags
                FROM user_favorites uf
                JOIN music m ON uf.music_id = m.id
                WHERE uf.user_id=?
                ORDER BY uf.created_at DESC
                LIMIT 300
                """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String artist = safeLower(rs.getString("artist"));
                    if (!artist.isEmpty()) {
                        artistCount.merge(artist, 1, Integer::sum);
                    }
                    String lang = safeLower(rs.getString("language"));
                    if (!lang.isEmpty()) {
                        langCount.merge(lang, 1, Integer::sum);
                    }
                    for (String tag : splitTags(rs.getString("tags"))) {
                        tagSet.add(tag.toLowerCase(Locale.ROOT));
                    }
                }
            }
        } catch (Exception e) {
            logger.error("加载用户画像失败 userId={}", userId, e);
        }
        return new UserProfile(topKeys(artistCount, 5), topKeys(langCount, 3), new ArrayList<>(tagSet));
    }

    private List<SongCandidate> loadCandidates(int userId, Set<Integer> favoriteIds, int limit) {
        List<SongCandidate> list = new ArrayList<>();
        String sql = """
                SELECT m.id, m.title, m.artist, m.album, m.language, m.tags, m.play_count, m.created_at
                FROM music m
                WHERE NOT EXISTS (
                    SELECT 1 FROM user_favorites uf
                    WHERE uf.user_id = ? AND uf.music_id = m.id
                )
                ORDER BY m.play_count DESC, m.created_at DESC
                LIMIT ?
                """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    if (favoriteIds.contains(id)) {
                        continue;
                    }
                    list.add(new SongCandidate(
                            id,
                            rs.getString("title"),
                            rs.getString("artist"),
                            rs.getString("album"),
                            rs.getString("language"),
                            rs.getString("tags"),
                            rs.getInt("play_count"),
                            rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toInstant()
                    ));
                }
            }
        } catch (Exception e) {
            logger.error("加载候选歌曲失败 userId={}", userId, e);
        }
        return list;
    }

    private static final double PENALTY_OWN_PLAYLIST = 2.5;
    private static final double PENALTY_FAVORITE_PLAYLIST = 1.2;

    private List<RecommendationItem> rankByRule(List<SongCandidate> candidates,
                                              UserProfile profile,
                                              Set<Integer> ownPlaylistMusicIds,
                                              Set<Integer> favoritePlaylistMusicIds) {
        List<RecommendationItem> list = new ArrayList<>();
        for (SongCandidate c : candidates) {
            double score = 0;
            if (profile.topArtists.contains(safeLower(c.artist))) {
                score += 3;
            }
            if (profile.topLanguages.contains(safeLower(c.language))) {
                score += 2;
            }
            Set<String> tags = splitTags(c.tags);
            for (String t : tags) {
                if (profile.topTags.contains(t.toLowerCase(Locale.ROOT))) {
                    score += 1.2;
                }
            }
            score += Math.log10(Math.max(1, c.playCount));
            if (c.createdAt != null) {
                long days = Duration.between(c.createdAt, Instant.now()).toDays();
                if (days <= 14) {
                    score += 1.5;
                }
            }
            if (ownPlaylistMusicIds.contains(c.id)) {
                score -= PENALTY_OWN_PLAYLIST;
            } else if (favoritePlaylistMusicIds.contains(c.id)) {
                score -= PENALTY_FAVORITE_PLAYLIST;
            }
            list.add(new RecommendationItem(c.id, score, "rule", "基于收藏风格匹配"));
        }
        list.sort(Comparator.comparingDouble(RecommendationItem::score).reversed());
        return list;
    }

    /** AI 重排后仍将歌单内曲目靠后排列，避免被顶到前列。 */
    private List<RecommendationItem> deprioritizePlaylistMusic(List<RecommendationItem> ranked,
                                                               Set<Integer> ownPlaylistMusicIds,
                                                               Set<Integer> favoritePlaylistMusicIds) {
        List<RecommendationItem> primary = new ArrayList<>();
        List<RecommendationItem> fromFavoritePlaylist = new ArrayList<>();
        List<RecommendationItem> fromOwnPlaylist = new ArrayList<>();
        for (RecommendationItem item : ranked) {
            if (ownPlaylistMusicIds.contains(item.musicId)) {
                fromOwnPlaylist.add(item);
            } else if (favoritePlaylistMusicIds.contains(item.musicId)) {
                fromFavoritePlaylist.add(item);
            } else {
                primary.add(item);
            }
        }
        primary.addAll(fromFavoritePlaylist);
        primary.addAll(fromOwnPlaylist);
        return primary;
    }

    private List<RecommendationItem> applyAiRerankIfEnabled(int userId,
                                                            UserProfile profile,
                                                            List<RecommendationItem> ranked,
                                                            List<SongCandidate> candidates) {
        if (!configManager.isRecommendationAiEnabled()) {
            return ranked;
        }
        String apiKey = configManager.getRecommendationAiApiKey();
        if (apiKey.isBlank()) {
            logger.warn("recommendation_ai.enabled=true 但 api_key 为空，回退规则排序");
            return ranked;
        }

        try {
            List<Integer> topIds = ranked.stream().limit(80).map(RecommendationItem::musicId).toList();
            Map<Integer, RecommendationItem> byId = ranked.stream()
                    .collect(Collectors.toMap(RecommendationItem::musicId, r -> r, (a, b) -> a, LinkedHashMap::new));
            Map<Integer, SongCandidate> candidateMap = candidates.stream()
                    .collect(Collectors.toMap(SongCandidate::id, c -> c, (a, b) -> a, LinkedHashMap::new));
            String response = callOpenAiForRerank(userId, profile, topIds, candidateMap);
            List<RecommendationItem> aiRanked = parseAiRerankResponse(response, byId);
            if (aiRanked.isEmpty()) {
                return ranked;
            }
            Set<Integer> added = aiRanked.stream().map(RecommendationItem::musicId).collect(Collectors.toSet());
            for (RecommendationItem item : ranked) {
                if (!added.contains(item.musicId)) {
                    aiRanked.add(item);
                }
            }
            return aiRanked;
        } catch (Exception e) {
            logger.error("AI 重排失败 userId={}", userId, e);
            return ranked;
        }
    }

    private String callOpenAiForRerank(int userId,
                                       UserProfile profile,
                                       List<Integer> candidateIds,
                                       Map<Integer, SongCandidate> candidateMap) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", configManager.getRecommendationAiModel());
        payload.put("temperature", configManager.getRecommendationAiTemperature());
        payload.put("top_p", configManager.getRecommendationAiTopP());
        payload.put("max_tokens", configManager.getRecommendationAiMaxTokens());

        List<Map<String, Object>> candidateMeta = new ArrayList<>();
        for (Integer id : candidateIds) {
            SongCandidate c = candidateMap.get(id);
            if (c == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.id);
            row.put("title", nullToEmpty(c.title));
            row.put("artist", nullToEmpty(c.artist));
            row.put("language", nullToEmpty(c.language));
            row.put("tags", nullToEmpty(c.tags));
            candidateMeta.add(row);
        }

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of(
                "role", "system",
                "content", "你是音乐推荐重排器。只返回严格JSON，不要markdown，不要代码块。"
                        + "输出字段: recommended_song_ids(int数组), reasons(对象: key是song_id字符串,value是中文一句理由)。"
                        + "理由必须只基于提供的候选歌曲信息(title/artist/language/tags)与用户画像，不得编造未提供的歌手或歌曲信息。"
        ));
        messages.add(Map.of(
                "role", "user",
                "content", "user_id=" + userId +
                        "\nprofile_top_artists=" + profile.topArtists +
                        "\nprofile_top_languages=" + profile.topLanguages +
                        "\nprofile_top_tags=" + profile.topTags +
                        "\ncandidates=" + objectMapper.writeValueAsString(candidateMeta) +
                        "\n要求：只从 candidates 的 id 中选择，且最多返回" + configManager.getRecommendationAiDailyLimit() + "首。"
                        + "\n每条理由长度 8-28 个中文字符，禁止出现乱码或控制字符。"
        ));
        payload.put("messages", messages);

        String body = objectMapper.writeValueAsString(payload);
        String url = configManager.getRecommendationAiBaseUrl() + "/chat/completions";
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(configManager.getRecommendationAiTimeoutSeconds()))
                .build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(configManager.getRecommendationAiTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + configManager.getRecommendationAiApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IllegalStateException("OpenAI HTTP " + resp.statusCode() + ": " + resp.body());
        }
        JsonNode root = objectMapper.readTree(resp.body());
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.asText().isBlank()) {
            throw new IllegalStateException("OpenAI 返回为空");
        }
        return content.asText();
    }

    private List<RecommendationItem> parseAiRerankResponse(String rawContent, Map<Integer, RecommendationItem> byId) {
        List<RecommendationItem> out = new ArrayList<>();
        try {
            JsonNode json = objectMapper.readTree(extractJson(rawContent));
            JsonNode arr = json.path("recommended_song_ids");
            JsonNode reasonsNode = json.path("reasons");
            if (!arr.isArray()) {
                return out;
            }
            for (JsonNode idNode : arr) {
                int id = idNode.asInt(-1);
                if (id <= 0 || !byId.containsKey(id)) {
                    continue;
                }
                String reason = "AI重排推荐";
                if (reasonsNode != null && reasonsNode.has(String.valueOf(id))) {
                    reason = sanitizeReason(reasonsNode.get(String.valueOf(id)).asText(reason));
                }
                RecommendationItem base = byId.get(id);
                out.add(new RecommendationItem(id, base.score, "ai", reason));
            }
        } catch (Exception ignore) {
            return List.of();
        }
        return out;
    }

    private List<RecommendationItem> strictFilterFavorites(List<RecommendationItem> ranked, Set<Integer> favoriteIds) {
        return ranked.stream()
                .filter(r -> !favoriteIds.contains(r.musicId))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<Map<String, Object>> loadRecommendationsFromRedis(int userId, LocalDate date) {
        String key = redisKey(userId, date);
        String payload = redisService.get(key);
        if (payload == null || payload.isBlank()) {
            return List.of();
        }
        try {
            JsonNode arr = objectMapper.readTree(payload);
            if (!arr.isArray()) {
                return List.of();
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            for (JsonNode n : arr) {
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("rank", n.path("rank").asInt());
                one.put("musicId", n.path("musicId").asInt());
                one.put("title", n.path("title").asText(""));
                one.put("artist", n.path("artist").asText(""));
                one.put("album", n.path("album").asText(""));
                one.put("language", n.path("language").asText(""));
                one.put("tags", n.path("tags").asText(""));
                one.put("score", n.path("score").asDouble(0));
                one.put("source", n.path("source").asText("rule"));
                one.put("reason", sanitizeReason(n.path("reason").asText("")));
                rows.add(one);
            }
            return rows;
        } catch (Exception e) {
            logger.error("解析Redis每日推荐失败 userId={} date={}", userId, date, e);
            return List.of();
        }
    }

    private void cacheRecommendations(int userId, LocalDate recDate, List<RecommendationItem> ranked, List<SongCandidate> candidates) {
        try {
            String key = redisKey(userId, recDate);
            Map<Integer, SongCandidate> songMap = candidates.stream()
                    .collect(Collectors.toMap(SongCandidate::id, c -> c, (a, b) -> a, LinkedHashMap::new));
            List<Map<String, Object>> data = new ArrayList<>();
            int rank = 1;
            for (RecommendationItem item : ranked) {
                SongCandidate song = songMap.get(item.musicId);
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("rank", rank++);
                one.put("musicId", item.musicId);
                one.put("title", song == null ? "" : nullToEmpty(song.title));
                one.put("artist", song == null ? "" : nullToEmpty(song.artist));
                one.put("album", song == null ? "" : nullToEmpty(song.album));
                one.put("language", song == null ? "" : nullToEmpty(song.language));
                one.put("tags", song == null ? "" : nullToEmpty(song.tags));
                one.put("score", item.score);
                one.put("source", item.source);
                one.put("reason", sanitizeReason(item.reason));
                data.add(one);
            }
            String payload = objectMapper.writeValueAsString(data);
            redisService.setWithExpiry(key, payload, 60 * 60 * 72);
        } catch (Exception e) {
            logger.error("写入推荐缓存失败 userId={} date={}", userId, recDate, e);
        }
    }

    private String redisKey(int userId, LocalDate recDate) {
        return "daily_reco:" + recDate + ":" + userId;
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static Set<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return Set.of();
        }
        String[] arr = tags.split("[,|/;，、\\s]+");
        Set<String> out = new LinkedHashSet<>();
        for (String t : arr) {
            if (t != null) {
                String x = t.trim();
                if (!x.isEmpty()) {
                    out.add(x);
                }
            }
        }
        return out;
    }

    private static String extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return "{}";
        }
        String s = raw.trim();
        if (s.startsWith("```")) {
            int first = s.indexOf('{');
            int last = s.lastIndexOf('}');
            if (first >= 0 && last > first) {
                return s.substring(first, last + 1);
            }
        }
        return s;
    }

    private static String sanitizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "AI重排推荐";
        }
        String cleaned = reason
                .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.length() > 40) {
            cleaned = cleaned.substring(0, 40);
        }
        if (cleaned.isBlank()) {
            return "AI重排推荐";
        }
        return cleaned;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static List<String> topKeys(Map<String, Integer> m, int topN) {
        return m.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(topN)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private record SongCandidate(
            int id,
            String title,
            String artist,
            String album,
            String language,
            String tags,
            int playCount,
            Instant createdAt
    ) {}

    private record RecommendationItem(
            int musicId,
            double score,
            String source,
            String reason
    ) {}

    private record UserProfile(
            List<String> topArtists,
            List<String> topLanguages,
            List<String> topTags
    ) {}
}
