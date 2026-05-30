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
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class DailyRecommendationService {
    private static final Logger logger = LoggerFactory.getLogger(DailyRecommendationService.class);
    private static final ZoneId CN_ZONE = ZoneId.of("Asia/Shanghai");

    /** 跨日推荐历史保留天数（用于降权/排除近期已推曲目） */
    private static final int HISTORY_RETENTION_DAYS = 14;
    /** 近 N 天内推荐过的曲目施加强降权，避免连续几天高度重合 */
    private static final int STRONG_RECENT_DAYS = 3;
    private static final double PENALTY_RECENT_STRONG = 12.0;
    private static final double PENALTY_RECENT_MEDIUM = 6.0;
    private static final double PENALTY_RECENT_LIGHT = 2.5;
    /** 单日列表内同一艺人最多入选曲目数 */
    private static final int MAX_SONGS_PER_ARTIST = 2;
    /** 单日列表内同一语种占比上限（超过则延后入选） */
    private static final double MAX_LANGUAGE_SHARE = 0.55;
    /** 按日期扰动排序的上限加分（确定性，同一天结果稳定） */
    private static final double DAY_JITTER_MAX = 1.4;

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
        List<SongCandidate> candidates = loadCandidates(userId, favoriteIds, recDate, candidateLimit);
        if (candidates.isEmpty()) {
            cacheRecommendations(userId, recDate, List.of(), List.of());
            return;
        }

        Map<Integer, SongCandidate> candidateById = candidates.stream()
                .collect(Collectors.toMap(SongCandidate::id, c -> c, (a, b) -> a, LinkedHashMap::new));
        Map<Integer, Integer> daysSinceRecommended = loadDaysSinceLastRecommended(userId, recDate);

        UserProfile profile = loadUserProfile(userId);
        List<RecommendationItem> ranked = rankByRule(candidates, profile, ownPlaylistMusicIds, favoritePlaylistMusicIds);
        ranked = applyCrossDayPenalty(ranked, daysSinceRecommended);
        ranked = applyDayScoreJitter(ranked, userId, recDate);
        ranked = applyAiRerankIfEnabled(userId, profile, ranked, candidates, recDate);
        ranked = deprioritizePlaylistMusic(ranked, ownPlaylistMusicIds, favoritePlaylistMusicIds);
        ranked = strictFilterFavorites(ranked, favoriteIds);

        int limit = configManager.getRecommendationAiDailyLimit();
        ranked = selectDiverseList(ranked, candidateById, limit);
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

    private List<SongCandidate> loadCandidates(int userId, Set<Integer> favoriteIds, LocalDate recDate, int limit) {
        List<SongCandidate> list = new ArrayList<>();
        int daySeed = (int) ((recDate.toEpochDay() * 31L + userId * 17L) % 997);
        if (daySeed <= 0) {
            daySeed = 1;
        }
        String sql = """
                SELECT m.id, m.title, m.artist, m.album, m.language, m.tags, m.play_count, m.created_at
                FROM music m
                WHERE NOT EXISTS (
                    SELECT 1 FROM user_favorites uf
                    WHERE uf.user_id = ? AND uf.music_id = m.id
                )
                ORDER BY (m.play_count + MOD(m.id * ?, 997)) DESC, m.created_at DESC
                LIMIT ?
                """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, daySeed);
            ps.setInt(3, limit);
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

    /**
     * 对近几日已推荐曲目降权，降低跨日列表重合度。
     * daysAgo=1 表示昨天推荐过，0 表示当天（生成前）已在缓存中。
     */
    private List<RecommendationItem> applyCrossDayPenalty(List<RecommendationItem> ranked,
                                                        Map<Integer, Integer> daysSinceRecommended) {
        if (daysSinceRecommended.isEmpty()) {
            return ranked;
        }
        List<RecommendationItem> adjusted = new ArrayList<>(ranked.size());
        for (RecommendationItem item : ranked) {
            Integer daysAgo = daysSinceRecommended.get(item.musicId);
            if (daysAgo == null) {
                adjusted.add(item);
                continue;
            }
            double penalty = 0;
            if (daysAgo <= STRONG_RECENT_DAYS) {
                penalty = PENALTY_RECENT_STRONG;
            } else if (daysAgo <= 7) {
                penalty = PENALTY_RECENT_MEDIUM;
            } else if (daysAgo <= HISTORY_RETENTION_DAYS) {
                penalty = PENALTY_RECENT_LIGHT;
            }
            adjusted.add(new RecommendationItem(
                    item.musicId,
                    item.score - penalty,
                    item.source,
                    item.reason
            ));
        }
        adjusted.sort(Comparator.comparingDouble(RecommendationItem::score).reversed());
        return adjusted;
    }

    /** 按用户+日期对分数做确定性微扰，使同日稳定、跨日排序有差异。 */
    private List<RecommendationItem> applyDayScoreJitter(List<RecommendationItem> ranked,
                                                         int userId,
                                                         LocalDate recDate) {
        List<RecommendationItem> adjusted = new ArrayList<>(ranked.size());
        for (RecommendationItem item : ranked) {
            double jitter = dayJitter(userId, recDate, item.musicId);
            adjusted.add(new RecommendationItem(
                    item.musicId,
                    item.score + jitter,
                    item.source,
                    item.reason
            ));
        }
        adjusted.sort(Comparator.comparingDouble(RecommendationItem::score).reversed());
        return adjusted;
    }

    private static double dayJitter(int userId, LocalDate recDate, int musicId) {
        long seed = recDate.toEpochDay() * 1_000_003L + userId * 100_019L + musicId * 1_009L;
        return (new Random(seed).nextDouble()) * DAY_JITTER_MAX;
    }

    /**
     * 在分数相近的池子里优先保证艺人/语种分散，避免单日列表过于同质。
     */
    private List<RecommendationItem> selectDiverseList(List<RecommendationItem> ranked,
                                                         Map<Integer, SongCandidate> candidateById,
                                                         int limit) {
        if (ranked.size() <= limit) {
            return new ArrayList<>(ranked);
        }
        List<RecommendationItem> selected = new ArrayList<>(limit);
        List<RecommendationItem> deferredArtist = new ArrayList<>();
        List<RecommendationItem> deferredLang = new ArrayList<>();
        Map<String, Integer> artistCount = new HashMap<>();
        Map<String, Integer> langCount = new HashMap<>();

        for (RecommendationItem item : ranked) {
            if (selected.size() >= limit) {
                break;
            }
            SongCandidate song = candidateById.get(item.musicId);
            String artist = song == null ? "" : safeLower(song.artist);
            String lang = song == null ? "" : safeLower(song.language);

            if (!artist.isEmpty() && artistCount.getOrDefault(artist, 0) >= MAX_SONGS_PER_ARTIST) {
                deferredArtist.add(item);
                continue;
            }
            int nextSize = selected.size() + 1;
            if (!lang.isEmpty()) {
                int langAfter = langCount.getOrDefault(lang, 0) + 1;
                if (nextSize >= 4 && (double) langAfter / nextSize > MAX_LANGUAGE_SHARE) {
                    deferredLang.add(item);
                    continue;
                }
            }
            selected.add(item);
            if (!artist.isEmpty()) {
                artistCount.merge(artist, 1, Integer::sum);
            }
            if (!lang.isEmpty()) {
                langCount.merge(lang, 1, Integer::sum);
            }
        }

        for (RecommendationItem item : deferredLang) {
            if (selected.size() >= limit) {
                break;
            }
            selected.add(item);
        }
        for (RecommendationItem item : deferredArtist) {
            if (selected.size() >= limit) {
                break;
            }
            selected.add(item);
        }
        return selected;
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
                                                            List<SongCandidate> candidates,
                                                            LocalDate recDate) {
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
            String response = callOpenAiForRerank(userId, profile, topIds, candidateMap, recDate);
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
                                       Map<Integer, SongCandidate> candidateMap,
                                       LocalDate recDate) throws Exception {
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
                        + "重排时优先保证列表多样性：同一艺人尽量不超过2首，语种与风格标签尽量分散，避免高度同质。"
        ));
        messages.add(Map.of(
                "role", "user",
                "content", "user_id=" + userId +
                        "\nrec_date=" + recDate +
                        "\nprofile_top_artists=" + profile.topArtists +
                        "\nprofile_top_languages=" + profile.topLanguages +
                        "\nprofile_top_tags=" + profile.topTags +
                        "\ncandidates=" + objectMapper.writeValueAsString(candidateMeta) +
                        "\n要求：只从 candidates 的 id 中选择，且最多返回" + configManager.getRecommendationAiDailyLimit() + "首。"
                        + "兼顾用户口味与当日新鲜感：可保留部分偏好匹配，但不要集中同一艺人/同一语种。"
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
            appendRecommendationHistory(userId, recDate, ranked);
        } catch (Exception e) {
            logger.error("写入推荐缓存失败 userId={} date={}", userId, recDate, e);
        }
    }

    private String redisKey(int userId, LocalDate recDate) {
        return "daily_reco:" + recDate + ":" + userId;
    }

    private String historyKey(int userId) {
        return "daily_reco_history:" + userId;
    }

    /** musicId -> 距 recDate 最近被推荐的天数（1=昨天，0=当天已有缓存） */
    private Map<Integer, Integer> loadDaysSinceLastRecommended(int userId, LocalDate recDate) {
        Map<Integer, Integer> daysAgo = new HashMap<>();
        for (HistoryDayEntry entry : loadRecommendationHistory(userId, recDate)) {
            if (entry.date.equals(recDate)) {
                continue;
            }
            long gap = recDate.toEpochDay() - entry.date.toEpochDay();
            if (gap <= 0 || gap > HISTORY_RETENTION_DAYS) {
                continue;
            }
            int days = (int) gap;
            for (Integer musicId : entry.musicIds) {
                daysAgo.merge(musicId, days, Math::min);
            }
        }
        return daysAgo;
    }

    private List<HistoryDayEntry> loadRecommendationHistory(int userId, LocalDate recDate) {
        Map<LocalDate, HistoryDayEntry> byDate = new LinkedHashMap<>();
        for (HistoryDayEntry entry : parseHistoryPayload(redisService.get(historyKey(userId)))) {
            byDate.put(entry.date, entry);
        }
        for (int i = 1; i <= HISTORY_RETENTION_DAYS; i++) {
            LocalDate d = recDate.minusDays(i);
            if (byDate.containsKey(d)) {
                continue;
            }
            List<Integer> ids = loadMusicIdsFromRedisDay(userId, d);
            if (!ids.isEmpty()) {
                byDate.put(d, new HistoryDayEntry(d, ids));
            }
        }
        return byDate.values().stream()
                .sorted(Comparator.comparing(HistoryDayEntry::date))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<Integer> loadMusicIdsFromRedisDay(int userId, LocalDate date) {
        List<Map<String, Object>> rows = loadRecommendationsFromRedis(userId, date);
        List<Integer> ids = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object id = row.get("musicId");
            if (id instanceof Number n) {
                ids.add(n.intValue());
            }
        }
        return ids;
    }

    private List<HistoryDayEntry> parseHistoryPayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return List.of();
        }
        try {
            JsonNode arr = objectMapper.readTree(payload);
            if (!arr.isArray()) {
                return List.of();
            }
            List<HistoryDayEntry> out = new ArrayList<>();
            for (JsonNode n : arr) {
                String dateStr = n.path("date").asText("");
                if (dateStr.isBlank()) {
                    continue;
                }
                LocalDate date = LocalDate.parse(dateStr);
                List<Integer> ids = new ArrayList<>();
                JsonNode idArr = n.path("musicIds");
                if (idArr.isArray()) {
                    for (JsonNode idNode : idArr) {
                        int id = idNode.asInt(-1);
                        if (id > 0) {
                            ids.add(id);
                        }
                    }
                }
                if (!ids.isEmpty()) {
                    out.add(new HistoryDayEntry(date, ids));
                }
            }
            return out;
        } catch (Exception e) {
            logger.warn("解析推荐历史失败", e);
            return List.of();
        }
    }

    private void appendRecommendationHistory(int userId, LocalDate recDate, List<RecommendationItem> ranked) {
        try {
            List<HistoryDayEntry> history = new ArrayList<>(loadRecommendationHistory(userId, recDate));
            history.removeIf(e -> e.date.equals(recDate));
            List<Integer> ids = ranked.stream().map(RecommendationItem::musicId).toList();
            if (!ids.isEmpty()) {
                history.add(new HistoryDayEntry(recDate, ids));
            }
            LocalDate cutoff = recDate.minusDays(HISTORY_RETENTION_DAYS);
            history = history.stream()
                    .filter(e -> !e.date.isBefore(cutoff))
                    .sorted(Comparator.comparing(HistoryDayEntry::date))
                    .collect(Collectors.toCollection(ArrayList::new));

            List<Map<String, Object>> serialized = new ArrayList<>();
            for (HistoryDayEntry entry : history) {
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("date", entry.date.toString());
                one.put("musicIds", entry.musicIds);
                serialized.add(one);
            }
            String payload = objectMapper.writeValueAsString(serialized);
            redisService.setWithExpiry(historyKey(userId), payload, 60 * 60 * 24 * (HISTORY_RETENTION_DAYS + 2));
        } catch (Exception e) {
            logger.warn("写入推荐历史失败 userId={} date={}", userId, recDate, e);
        }
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

    private record HistoryDayEntry(LocalDate date, List<Integer> musicIds) {}
}
