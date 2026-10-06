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
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DailyRecommendationService {
    private static final Logger logger = LoggerFactory.getLogger(DailyRecommendationService.class);
    private static final ZoneId CN_ZONE = ZoneId.of("Asia/Shanghai");

    /** 跨日推荐历史保留天数（用于降权/排除近期已推曲目） */
    private static final int HISTORY_RETENTION_DAYS = 14;
    /** 近 N 天内推荐过的曲目施加强降权，避免连续几天高度重合 */
    private static final int STRONG_RECENT_DAYS = 3;
    /**
     * 降权幅度按新分值量程标定（口味上限约 7.4、热度 2.0、新歌 1.5）：
     * 近 3 天推过的曲目会被压到绝大多数候选之下，但仍保留分档梯度。
     */
    private static final double PENALTY_RECENT_STRONG = 6.0;
    private static final double PENALTY_RECENT_MEDIUM = 3.0;
    private static final double PENALTY_RECENT_LIGHT = 1.5;
    /** 单日列表内同一艺人最多入选曲目数 */
    private static final int MAX_SONGS_PER_ARTIST = 2;
    /** 单日列表内同一专辑最多入选曲目数，避免整张专辑占位 */
    private static final int MAX_SONGS_PER_ALBUM = 3;
    /** 单日列表内同一语种占比上限（超过则延后入选） */
    private static final double MAX_LANGUAGE_SHARE = 0.55;
    /** 按日期扰动排序的上限加分（确定性，同一天结果稳定） */
    private static final double DAY_JITTER_MAX = 0.8;

    /** 画像命中项的单项上限分：按画像权重线性缩放，避免布尔命中让强/弱偏好同分 */
    private static final double ARTIST_MATCH_MAX = 3.0;
    private static final double LANGUAGE_MATCH_MAX = 2.0;
    private static final double TAG_MATCH_MAX = 2.4;
    /** 热度分上限：按候选集内百分位归一化，避免全局播放量压过口味匹配 */
    private static final double POPULARITY_MAX = 2.0;
    /** 新歌加分及其时间窗口 */
    private static final double FRESHNESS_BONUS = 1.5;
    private static final int FRESHNESS_DAYS = 14;
    /** 召回时使用的画像维度数量 */
    private static final int TOP_RECALL_ARTISTS = 10;
    private static final int TOP_RECALL_LANGUAGES = 3;
    private static final int TOP_RECALL_TAGS = 8;
    /** 协同过滤（同好共现）召回的种子收藏数与得分上限 */
    private static final int CF_SEED_FAVORITES = 30;
    private static final double CF_MATCH_MAX = 2.8;
    /** 社区收藏数（被多少人收藏）得分上限，作为 play_count 之外的社群热度信号，冷启动尤其依赖它 */
    private static final double COMMUNITY_MATCH_MAX = 2.0;
    /** 歌单共现召回的得分上限：同一歌单内与已收藏曲目同现，作为人工策展的强口味信号 */
    private static final double PLAYLIST_MATCH_MAX = 2.0;
    /** 候选查询公共列与公共过滤条件 */
    private static final String CANDIDATE_COLUMNS =
            "SELECT m.id, m.title, m.artist, m.album, m.language, m.tags, m.play_count, m.created_at FROM music m";
    private static final String CANDIDATE_NOT_FAVORITED =
            " WHERE NOT EXISTS (SELECT 1 FROM user_favorites uf WHERE uf.user_id = ? AND uf.music_id = m.id)";

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
        // 画像先于候选加载：多路召回需要用到用户偏好
        UserProfile profile = loadUserProfile(userId);
        int candidateLimit = configManager.getRecommendationAiDailyLimit() * 8;
        CandidatePool pool = loadCandidates(userId, favoriteIds, profile, recDate, candidateLimit);
        List<SongCandidate> candidates = pool.candidates();
        if (candidates.isEmpty()) {
            cacheRecommendations(userId, recDate, List.of(), List.of());
            return;
        }

        Map<Integer, SongCandidate> candidateById = indexBy(candidates, SongCandidate::id);
        Map<Integer, Integer> daysSinceRecommended = loadDaysSinceLastRecommended(userId, recDate);

        List<RecommendationItem> ranked = rankByRule(candidates, profile, ownPlaylistMusicIds,
                favoritePlaylistMusicIds, pool.collaborativeScores(), pool.communityScores(),
                pool.playlistScores());
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
        return queryMusicIds("SELECT music_id FROM user_favorites WHERE user_id=? ORDER BY created_at DESC",
                userId, "查询用户收藏失败 userId={}");
    }

    /** 用户自建歌单内曲目：降权，仍可能进入推荐。 */
    private Set<Integer> loadOwnPlaylistMusicIds(int userId) {
        return queryMusicIds("""
                SELECT DISTINCT pm.music_id
                FROM playlist_music pm
                INNER JOIN playlists p ON p.id = pm.playlist_id
                WHERE p.user_id = ?
                """, userId, "查询用户歌单曲目失败 userId={}");
    }

    /** 用户收藏歌单内曲目：降权（弱于自建歌单），仍可能进入推荐。 */
    private Set<Integer> loadFavoritePlaylistMusicIds(int userId) {
        return queryMusicIds("""
                SELECT DISTINCT pm.music_id
                FROM playlist_music pm
                INNER JOIN user_favorite_playlists ufp ON ufp.playlist_id = pm.playlist_id
                WHERE ufp.user_id = ?
                """, userId, "查询收藏歌单曲目失败 userId={}");
    }

    private Set<Integer> queryMusicIds(String sql, int userId, String errorMessage) {
        // 保序：协同过滤召回要用"最近收藏"当种子
        Set<Integer> ids = new LinkedHashSet<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("music_id"));
                }
            }
        } catch (Exception e) {
            logger.error(errorMessage, userId, e);
        }
        return ids;
    }

    private UserProfile loadUserProfile(int userId) {
        Map<String, Integer> artistCount = new HashMap<>();
        Map<String, Integer> langCount = new HashMap<>();
        Map<String, Integer> tagCount = new HashMap<>();

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
                        tagCount.merge(tag.toLowerCase(Locale.ROOT), 1, Integer::sum);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("加载用户画像失败 userId={}", userId, e);
        }
        return new UserProfile(normalizeCounts(artistCount), normalizeCounts(langCount), normalizeCounts(tagCount));
    }

    /** 把频次画像归一化到 (0,1]，最高频维度为 1，作为加权相似度使用。 */
    private static Map<String, Double> normalizeCounts(Map<String, Integer> counts) {
        if (counts.isEmpty()) {
            return Map.of();
        }
        int max = counts.values().stream().mapToInt(Integer::intValue).max().orElse(1);
        Map<String, Double> weights = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            weights.put(entry.getKey(), entry.getValue() / (double) max);
        }
        return weights;
    }

    /**
     * 多路召回：口味（同艺人/标签/语种）优先，其次新歌，最后用热门补齐。
     * 避免只按全局播放量取前 N，导致长尾口味曲目永远进不了候选。
     * 收藏曲目由各查询的 NOT EXISTS 与后续 strictFilterFavorites 双重排除，这里不再重复过滤。
     */
    private CandidatePool loadCandidates(int userId, Set<Integer> favoriteIds, UserProfile profile,
                                         LocalDate recDate, int limit) {
        Map<Integer, SongCandidate> merged = new LinkedHashMap<>();
        int tasteLimit = Math.max(50, limit / 2);
        addCandidates(merged, loadTasteCandidates(userId, profile, tasteLimit), limit);
        ScoredRecall collaborative = loadCollaborativeCandidates(userId, favoriteIds,
                Math.max(50, limit / 3), limit);
        addCandidates(merged, collaborative.candidates(), limit);
        ScoredRecall community = loadCommunityCandidates(userId, Math.max(50, limit / 2), limit);
        addCandidates(merged, community.candidates(), limit);
        ScoredRecall playlist = loadPlaylistCandidates(userId, favoriteIds, Math.max(50, limit / 6), limit);
        addCandidates(merged, playlist.candidates(), limit);
        addCandidates(merged, loadFreshCandidates(userId, Math.max(30, limit / 8)), limit);
        addCandidates(merged, loadPopularCandidates(userId, recDate, limit), limit);
        return new CandidatePool(new ArrayList<>(merged.values()), collaborative.scores(),
                community.scores(), playlist.scores());
    }

    private void addCandidates(Map<Integer, SongCandidate> merged, List<SongCandidate> from, int cap) {
        for (SongCandidate candidate : from) {
            if (merged.size() >= cap) {
                return;
            }
            merged.putIfAbsent(candidate.id(), candidate);
        }
    }

    /**
     * 口味召回：艺人、标签、语种分别查询后合并。
     * 必须分开查——合成一条 OR 查询时，只要某个维度覆盖了全库（例如整库语种都是"未知语言"），
     * 结果会按播放量被这个维度占满，把真正精准的艺人/标签命中挤掉。
     * 标签为空时该维度直接跳过，不影响艺人/语种召回。
     */
    private List<SongCandidate> loadTasteCandidates(int userId, UserProfile profile, int limit) {
        List<String> artists = topKeys(profile.artistWeights(), TOP_RECALL_ARTISTS);
        List<String> tags = topKeys(profile.tagWeights(), TOP_RECALL_TAGS);
        List<String> languages = topKeys(profile.languageWeights(), TOP_RECALL_LANGUAGES);
        int perDimension = Math.max(30, limit / 3);
        Map<Integer, SongCandidate> merged = new LinkedHashMap<>();
        addCandidates(merged, queryByExactColumn(userId, "m.artist", artists, perDimension), limit);
        addCandidates(merged, queryByTagLike(userId, tags, perDimension), limit);
        // 语种区分度最低，放最后：即使整库都是"未知语言"也只占它自己的配额
        addCandidates(merged, queryByExactColumn(userId, "m.language", languages, perDimension), limit);
        return new ArrayList<>(merged.values());
    }

    /** 按等值列召回（小写比较）；column 只由调用方传入固定字面量，不接受外部输入。 */
    private List<SongCandidate> queryByExactColumn(int userId, String column, List<String> values, int limit) {
        if (values.isEmpty()) {
            return List.of();
        }
        String sql = CANDIDATE_COLUMNS + CANDIDATE_NOT_FAVORITED
                + " AND LOWER(" + column + ") IN (" + placeholders(values.size()) + ")"
                + " ORDER BY m.play_count DESC, m.created_at DESC LIMIT ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            int index = 2;
            for (String value : values) {
                ps.setString(index++, value);
            }
            ps.setInt(index, limit);
            return readCandidates(ps);
        } catch (Exception e) {
            logger.error("口味召回失败 userId={} column={}", userId, column, e);
            return List.of();
        }
    }

    private List<SongCandidate> queryByTagLike(int userId, List<String> tags, int limit) {
        if (tags.isEmpty()) {
            return List.of();
        }
        List<String> clauses = new ArrayList<>(tags.size());
        for (int i = 0; i < tags.size(); i++) {
            clauses.add("LOWER(m.tags) LIKE ?");
        }
        String sql = CANDIDATE_COLUMNS + CANDIDATE_NOT_FAVORITED
                + " AND (" + String.join(" OR ", clauses) + ")"
                + " ORDER BY m.play_count DESC, m.created_at DESC LIMIT ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            int index = 2;
            for (String tag : tags) {
                ps.setString(index++, "%" + tag + "%");
            }
            ps.setInt(index, limit);
            return readCandidates(ps);
        } catch (Exception e) {
            logger.error("标签召回失败 userId={}", userId, e);
            return List.of();
        }
    }

    /** 新歌召回：按上传时间倒序，保证新曲目有曝光机会。 */
    private List<SongCandidate> loadFreshCandidates(int userId, int limit) {
        String sql = CANDIDATE_COLUMNS + CANDIDATE_NOT_FAVORITED
                + " ORDER BY m.created_at DESC, m.id DESC LIMIT ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, limit);
            return readCandidates(ps);
        } catch (Exception e) {
            logger.error("新歌召回失败 userId={}", userId, e);
            return List.of();
        }
    }

    /**
     * 热门召回：有播放的曲目按真实播放量优先（线上播放量长尾极短，这才是"热门"），
     * 无播放的曲目再用日期扰动打散，兼顾质量与每日新鲜度。
     */
    private List<SongCandidate> loadPopularCandidates(int userId, LocalDate recDate, int limit) {
        int daySeed = (int) ((recDate.toEpochDay() * 31L + userId * 17L) % 997);
        if (daySeed <= 0) {
            daySeed = 1;
        }
        String sql = CANDIDATE_COLUMNS + CANDIDATE_NOT_FAVORITED
                + " ORDER BY (CASE WHEN m.play_count > 0 THEN 0 ELSE 1 END), m.play_count DESC,"
                + " MOD(m.id * ?, 997), m.created_at DESC LIMIT ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, daySeed);
            ps.setInt(3, limit);
            return readCandidates(ps);
        } catch (Exception e) {
            logger.error("热门召回失败 userId={}", userId, e);
            return List.of();
        }
    }

    /**
     * 协同过滤召回：以用户最近收藏的若干首为种子，找出"同好"（收藏过相同曲目的用户）
     * 也收藏过的其它曲目。完全不依赖 tags/artist 等文本画像，标签为空的曲库同样有效。
     * 查询可能因热门种子变重，因此限制种子数并用 try/catch 兜底，失败时静默退化为其它召回。
     */
    private ScoredRecall loadCollaborativeCandidates(int userId, Set<Integer> favoriteIds,
                                                     int limit, int poolLimit) {
        List<Integer> seeds = favoriteIds.stream().limit(CF_SEED_FAVORITES).toList();
        if (seeds.isEmpty()) {
            return ScoredRecall.empty();
        }
        String sql = """
                SELECT m.id, m.title, m.artist, m.album, m.language, m.tags, m.play_count, m.created_at,
                       SUM(1.0 / LOG(2 + uc.c)) AS co
                FROM user_favorites uf1
                JOIN user_favorites uf2 ON uf2.user_id = uf1.user_id AND uf2.music_id <> uf1.music_id
                JOIN (
                    SELECT user_id, COUNT(*) AS c
                    FROM user_favorites
                    GROUP BY user_id
                ) uc ON uc.user_id = uf2.user_id
                JOIN music m ON m.id = uf2.music_id
                WHERE uf1.music_id IN (%s)
                GROUP BY m.id
                ORDER BY co DESC, m.play_count DESC
                LIMIT ?
                """.formatted(placeholders(seeds.size()));
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int index = 1;
            for (Integer seed : seeds) {
                ps.setInt(index++, seed);
            }
            ps.setInt(index, limit);
            List<SongCandidate> candidates = new ArrayList<>();
            Map<Integer, Double> scores = new LinkedHashMap<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SongCandidate song = readCandidateRow(rs);
                    if (favoriteIds.contains(song.id())) {
                        continue;
                    }
                    if (candidates.size() >= poolLimit) {
                        break;
                    }
                    candidates.add(song);
                    scores.put(song.id(), rs.getDouble("co"));
                }
            }
            return new ScoredRecall(candidates, scores);
        } catch (Exception e) {
            logger.error("协同过滤召回失败 userId={}", userId, e);
            return ScoredRecall.empty();
        }
    }

    /**
     * 社区热门召回：按"被多少人收藏"排序。play_count 线上极不可靠（80% 为 0 且存在离群值），
     * 收藏数是更干净的社群认可信号，也是无收藏用户冷启动的主要候选来源。
     */
    private ScoredRecall loadCommunityCandidates(int userId, int limit, int poolLimit) {
        String sql = """
                SELECT m.id, m.title, m.artist, m.album, m.language, m.tags, m.play_count, m.created_at,
                       COUNT(uf.user_id) AS fav_count
                FROM user_favorites uf
                JOIN music m ON m.id = uf.music_id
                WHERE NOT EXISTS (
                    SELECT 1 FROM user_favorites uf_me
                    WHERE uf_me.user_id = ? AND uf_me.music_id = m.id
                )
                GROUP BY m.id
                ORDER BY fav_count DESC, m.play_count DESC
                LIMIT ?
                """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, limit);
            List<SongCandidate> candidates = new ArrayList<>();
            Map<Integer, Double> scores = new LinkedHashMap<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (candidates.size() >= poolLimit) {
                        break;
                    }
                    SongCandidate song = readCandidateRow(rs);
                    candidates.add(song);
                    scores.put(song.id(), rs.getDouble("fav_count"));
                }
            }
            return new ScoredRecall(candidates, scores);
        } catch (Exception e) {
            logger.error("社区热门召回失败 userId={}", userId, e);
            return ScoredRecall.empty();
        }
    }

    /**
     * 歌单共现召回：与用户已收藏曲目出现在同一歌单里的曲目，视为人工策展的强口味信号。
     * 只统计 <=200 首的歌单：极少数巨型歌单会贡献大量噪声，并让 playlist_music 自连接平方膨胀，
     * 所以在建表子查询里直接过滤；得分按歌单大小对数衰减，小歌单的同现更有信息量。
     */
    private ScoredRecall loadPlaylistCandidates(int userId, Set<Integer> favoriteIds,
                                                int limit, int poolLimit) {
        List<Integer> seeds = favoriteIds.stream().limit(CF_SEED_FAVORITES).toList();
        if (seeds.isEmpty()) {
            return ScoredRecall.empty();
        }
        String sql = """
                SELECT m.id, m.title, m.artist, m.album, m.language, m.tags, m.play_count, m.created_at,
                       SUM(1.0 / LOG(2 + plc.c)) AS co
                FROM playlist_music pm1
                JOIN playlist_music pm2 ON pm2.playlist_id = pm1.playlist_id AND pm2.music_id <> pm1.music_id
                JOIN (
                    SELECT playlist_id, COUNT(*) AS c
                    FROM playlist_music
                    GROUP BY playlist_id
                    HAVING COUNT(*) <= 200
                ) plc ON plc.playlist_id = pm2.playlist_id
                JOIN music m ON m.id = pm2.music_id
                WHERE pm1.music_id IN (%s)
                  AND NOT EXISTS (
                      SELECT 1 FROM user_favorites uf
                      WHERE uf.user_id = ? AND uf.music_id = m.id
                  )
                GROUP BY m.id
                ORDER BY co DESC, m.play_count DESC
                LIMIT ?
                """.formatted(placeholders(seeds.size()));
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int index = 1;
            for (Integer seed : seeds) {
                ps.setInt(index++, seed);
            }
            ps.setInt(index++, userId);
            ps.setInt(index, limit);
            List<SongCandidate> candidates = new ArrayList<>();
            Map<Integer, Double> scores = new LinkedHashMap<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SongCandidate song = readCandidateRow(rs);
                    if (favoriteIds.contains(song.id())) {
                        continue;
                    }
                    if (candidates.size() >= poolLimit) {
                        break;
                    }
                    candidates.add(song);
                    scores.put(song.id(), rs.getDouble("co"));
                }
            }
            return new ScoredRecall(candidates, scores);
        } catch (Exception e) {
            logger.error("歌单共现召回失败 userId={}", userId, e);
            return ScoredRecall.empty();
        }
    }

    private List<SongCandidate> readCandidates(PreparedStatement ps) throws Exception {
        List<SongCandidate> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(readCandidateRow(rs));
            }
        }
        return list;
    }

    private static SongCandidate readCandidateRow(ResultSet rs) throws SQLException {
        var createdAt = rs.getTimestamp("created_at");
        return new SongCandidate(
                rs.getInt("id"),
                rs.getString("title"),
                rs.getString("artist"),
                rs.getString("album"),
                rs.getString("language"),
                rs.getString("tags"),
                rs.getInt("play_count"),
                createdAt == null ? null : createdAt.toInstant()
        );
    }

    private static String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }

    private static final double PENALTY_OWN_PLAYLIST = 2.5;
    private static final double PENALTY_FAVORITE_PLAYLIST = 1.2;

    private List<RecommendationItem> rankByRule(List<SongCandidate> candidates,
                                              UserProfile profile,
                                              Set<Integer> ownPlaylistMusicIds,
                                              Set<Integer> favoritePlaylistMusicIds,
                                              Map<Integer, Double> collaborativeScores,
                                              Map<Integer, Double> communityScores,
                                              Map<Integer, Double> playlistScores) {
        Map<String, Double> tagWeights = weightTagsByRarity(profile.tagWeights(), candidates);
        Map<Integer, Double> collaborativeWeights = normalizeCountScores(collaborativeScores);
        Map<Integer, Double> communityWeights = normalizeCountScores(communityScores);
        Map<Integer, Double> playlistWeights = normalizeCountScores(playlistScores);
        double[] popularityScores = buildPopularityScores(candidates);
        List<RecommendationItem> list = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            SongCandidate c = candidates.get(i);
            double score = 0;

            Double artistWeight = profile.artistWeights().get(safeLower(c.artist));
            if (artistWeight != null) {
                score += ARTIST_MATCH_MAX * artistWeight;
            }
            Double languageWeight = profile.languageWeights().get(safeLower(c.language));
            if (languageWeight != null) {
                score += LANGUAGE_MATCH_MAX * languageWeight;
            }
            double tagScore = 0;
            for (String t : splitTags(c.tags)) {
                Double tagWeight = tagWeights.get(t.toLowerCase(Locale.ROOT));
                if (tagWeight != null) {
                    tagScore += tagWeight;
                }
            }
            score += TAG_MATCH_MAX * Math.min(1.0, tagScore);

            Double collaborativeWeight = collaborativeWeights.get(c.id);
            if (collaborativeWeight != null) {
                score += CF_MATCH_MAX * collaborativeWeight;
            }
            Double communityWeight = communityWeights.get(c.id);
            if (communityWeight != null) {
                score += COMMUNITY_MATCH_MAX * communityWeight;
            }
            Double playlistWeight = playlistWeights.get(c.id);
            if (playlistWeight != null) {
                score += PLAYLIST_MATCH_MAX * playlistWeight;
            }
            score += POPULARITY_MAX * popularityScores[i];
            if (c.createdAt != null) {
                long days = Duration.between(c.createdAt, Instant.now()).toDays();
                if (days <= FRESHNESS_DAYS) {
                    score += FRESHNESS_BONUS;
                }
            }
            if (ownPlaylistMusicIds.contains(c.id)) {
                score -= PENALTY_OWN_PLAYLIST;
            } else if (favoritePlaylistMusicIds.contains(c.id)) {
                score -= PENALTY_FAVORITE_PLAYLIST;
            }
            list.add(new RecommendationItem(c.id, score, "rule", "基于收藏风格匹配"));
        }
        sortByScoreDesc(list);
        return list;
    }

    /**
     * 标签权重 = 画像频次权重 × 候选集内 IDF，再归一化到 (0,1]。
     * 让"收藏里高频且全站少见"的标签比"到处都有的大众标签"更具区分度。
     */
    private static Map<String, Double> weightTagsByRarity(Map<String, Double> profileTagWeights,
                                                          List<SongCandidate> candidates) {
        if (profileTagWeights.isEmpty() || candidates.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (SongCandidate candidate : candidates) {
            for (String tag : splitTags(candidate.tags)) {
                documentFrequency.merge(tag.toLowerCase(Locale.ROOT), 1, Integer::sum);
            }
        }
        double total = candidates.size();
        Map<String, Double> weighted = new HashMap<>();
        double max = 0;
        for (Map.Entry<String, Double> entry : profileTagWeights.entrySet()) {
            double idf = Math.log((total + 1.0) / (documentFrequency.getOrDefault(entry.getKey(), 0) + 1.0)) + 1.0;
            double weight = entry.getValue() * idf;
            weighted.put(entry.getKey(), weight);
            max = Math.max(max, weight);
        }
        if (max <= 0) {
            return Map.of();
        }
        for (Map.Entry<String, Double> entry : weighted.entrySet()) {
            entry.setValue(entry.getValue() / max);
        }
        return weighted;
    }

    /**
     * 热度分（0~1）：以候选集内有播放曲目的 p99 为参考做对数缩放，0 播放记为 0。
     * 线上数据里 80% 的曲目播放量为 0、且存在一个 1e8 量级的离群值，用百分位会让
     * 绝大多数曲目挤在 0.8 附近失去区分度，用最大值做对数缩放又会被离群值压平，
     * 因此取 p99 作为鲁棒上限。
     */
    private static double[] buildPopularityScores(List<SongCandidate> candidates) {
        int size = candidates.size();
        double[] scores = new double[size];
        if (size == 0) {
            return scores;
        }
        List<Integer> played = new ArrayList<>();
        for (SongCandidate candidate : candidates) {
            if (candidate.playCount > 0) {
                played.add(candidate.playCount);
            }
        }
        if (played.isEmpty()) {
            return scores;
        }
        Collections.sort(played);
        int p99Index = (int) Math.floor((played.size() - 1) * 0.99);
        double denominator = Math.log1p(Math.max(1, played.get(p99Index)));
        if (denominator <= 0) {
            return scores;
        }
        for (int i = 0; i < size; i++) {
            int playCount = candidates.get(i).playCount;
            if (playCount > 0) {
                scores[i] = Math.min(1.0, Math.log1p(playCount) / denominator);
            }
        }
        return scores;
    }

    /** 计数类信号（同好共现、社区收藏）取对数后归一化到 (0,1]，避免高计数曲目单点压过其它信号。 */
    private static Map<Integer, Double> normalizeCountScores(Map<Integer, ? extends Number> scores) {
        if (scores.isEmpty()) {
            return Map.of();
        }
        double maxLog = 0;
        for (Number count : scores.values()) {
            maxLog = Math.max(maxLog, Math.log1p(count.doubleValue()));
        }
        if (maxLog <= 0) {
            return Map.of();
        }
        Map<Integer, Double> normalized = new HashMap<>();
        for (Map.Entry<Integer, ? extends Number> entry : scores.entrySet()) {
            normalized.put(entry.getKey(), Math.log1p(entry.getValue().doubleValue()) / maxLog);
        }
        return normalized;
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
            adjusted.add(withScore(item, item.score - penalty));
        }
        sortByScoreDesc(adjusted);
        return adjusted;
    }

    /** 按用户+日期对分数做确定性微扰，使同日稳定、跨日排序有差异。 */
    private List<RecommendationItem> applyDayScoreJitter(List<RecommendationItem> ranked,
                                                         int userId,
                                                         LocalDate recDate) {
        List<RecommendationItem> adjusted = new ArrayList<>(ranked.size());
        for (RecommendationItem item : ranked) {
            double jitter = dayJitter(userId, recDate, item.musicId);
            adjusted.add(withScore(item, item.score + jitter));
        }
        sortByScoreDesc(adjusted);
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
        // 因艺人/语种上限被推迟的曲目统一进一个队列（保持分数序），避免两个队列合并打乱排序
        List<RecommendationItem> deferred = new ArrayList<>();
        Map<String, Integer> artistCount = new HashMap<>();
        Map<String, Integer> albumCount = new HashMap<>();
        Map<String, Integer> langCount = new HashMap<>();

        for (RecommendationItem item : ranked) {
            if (selected.size() >= limit) {
                break;
            }
            SongCandidate song = candidateById.get(item.musicId);
            String artist = song == null ? "" : safeLower(song.artist);
            String album = song == null ? "" : safeLower(song.album);
            String lang = song == null ? "" : safeLower(song.language);

            boolean artistFull = !artist.isEmpty()
                    && artistCount.getOrDefault(artist, 0) >= MAX_SONGS_PER_ARTIST;
            boolean albumFull = !album.isEmpty()
                    && albumCount.getOrDefault(album, 0) >= MAX_SONGS_PER_ALBUM;
            boolean langFull = false;
            if (!lang.isEmpty()) {
                int nextSize = selected.size() + 1;
                int langAfter = langCount.getOrDefault(lang, 0) + 1;
                langFull = nextSize >= 4 && (double) langAfter / nextSize > MAX_LANGUAGE_SHARE;
            }
            if (artistFull || albumFull || langFull) {
                deferred.add(item);
                continue;
            }
            selected.add(item);
            if (!artist.isEmpty()) {
                artistCount.merge(artist, 1, Integer::sum);
            }
            if (!album.isEmpty()) {
                albumCount.merge(album, 1, Integer::sum);
            }
            if (!lang.isEmpty()) {
                langCount.merge(lang, 1, Integer::sum);
            }
        }

        for (RecommendationItem item : deferred) {
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
            Map<Integer, RecommendationItem> byId = indexBy(ranked, RecommendationItem::musicId);
            Map<Integer, SongCandidate> candidateMap = indexBy(candidates, SongCandidate::id);
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
                        "\nprofile_top_artists=" + topKeys(profile.artistWeights(), 5) +
                        "\nprofile_top_languages=" + topKeys(profile.languageWeights(), 3) +
                        "\nprofile_top_tags=" + topKeys(profile.tagWeights(), 10) +
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
            Map<Integer, SongCandidate> songMap = indexBy(candidates, SongCandidate::id);
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

    /** 按主键索引列表，保留插入顺序；重复键保留先出现者。 */
    private static <K, V> Map<K, V> indexBy(List<V> list, Function<V, K> keyFn) {
        return list.stream()
                .collect(Collectors.toMap(keyFn, v -> v, (a, b) -> a, LinkedHashMap::new));
    }

    private static void sortByScoreDesc(List<RecommendationItem> items) {
        items.sort(Comparator.comparingDouble(RecommendationItem::score).reversed());
    }

    private static RecommendationItem withScore(RecommendationItem item, double score) {
        return new RecommendationItem(item.musicId, score, item.source, item.reason);
    }

    private static List<String> topKeys(Map<String, Double> weights, int topN) {
        return weights.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
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
            Map<String, Double> artistWeights,
            Map<String, Double> languageWeights,
            Map<String, Double> tagWeights
    ) {}

    /** 候选池及三路协同信号：同好共现、社区收藏数、歌单共现（musicId -> 权重）。 */
    private record CandidatePool(
            List<SongCandidate> candidates,
            Map<Integer, Double> collaborativeScores,
            Map<Integer, Double> communityScores,
            Map<Integer, Double> playlistScores
    ) {}

    private record ScoredRecall(
            List<SongCandidate> candidates,
            Map<Integer, Double> scores
    ) {
        static ScoredRecall empty() {
            return new ScoredRecall(List.of(), Map.of());
        }
    }

    private record HistoryDayEntry(LocalDate date, List<Integer> musicIds) {}
}
