package com.neko.music.service;

import com.neko.music.Main;
import com.neko.music.config.ConfigManager;
import com.neko.music.util.LrcValidator;
import com.neko.music.util.RuntimeDiskGuard;
import com.neko.music.util.SongLanguageInferer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 本地单曲搜索无结果时：从 NeteaseCloudMusicApi 拉取一首匹配曲并走管理员入库流程。
 */
public class NeteaseSearchFillService {
    private static final Logger logger = LoggerFactory.getLogger(NeteaseSearchFillService.class);

    private static final int REDIS_LOCK_SECONDS = 180;
    private static final int PEER_WAIT_MAX_MS = 90_000;
    private static final int PEER_WAIT_POLL_MS = 500;

    /** 同一 JVM 内按搜索词串行化补全，避免并发双份入库 */
    private static final ConcurrentHashMap<String, ReentrantLock> QUERY_LOCKS = new ConcurrentHashMap<>();

    private final ConfigManager config;
    private final NeteaseCloudMusicClient neteaseClient;
    private final AdminMusicIngestService ingestService;
    private final RedisService redisService;

    public NeteaseSearchFillService(
            ConfigManager config,
            NeteaseCloudMusicClient neteaseClient,
            AdminMusicIngestService ingestService,
            RedisService redisService
    ) {
        this.config = config;
        this.neteaseClient = neteaseClient;
        this.ingestService = ingestService;
        this.redisService = redisService;
    }

    public enum FillReason {
        /** 补全成功或未触发补全 */
        NONE,
        /** 网易云无匹配或无法取链/入库 */
        NOT_FOUND,
        /** API 未登录，高音质不可用（常因 Cookie 过期） */
        LOGIN_EXPIRED,
        /** 其它异常 */
        ERROR,
        /** 运行目录磁盘可用空间不足 */
        LOW_DISK_SPACE
    }

    public record FillAttempt(Optional<AdminMusicIngestService.IngestedMusic> music, FillReason reason) {
        public static FillAttempt skipped() {
            return new FillAttempt(Optional.empty(), FillReason.NONE);
        }
    }

    public FillAttempt tryFillFromNetease(String query) {
        if (!config.isNeteaseSearchFillEnabled()) {
            return FillAttempt.skipped();
        }
        if (query == null || query.isBlank()) {
            return FillAttempt.skipped();
        }
        String trimmed = query.trim();
        RuntimeDiskGuard.logStorageForOperation("网易云自动补全", "query=" + trimmed);
        if (!RuntimeDiskGuard.hasSufficientSpaceForMusicWrites()) {
            return new FillAttempt(Optional.empty(), FillReason.LOW_DISK_SPACE);
        }
        if (config.getNeteaseApiBaseUrl().isBlank()) {
            logger.warn("netease_search_fill 已启用但 api_base_url 为空");
            return new FillAttempt(Optional.empty(), FillReason.ERROR);
        }

        String lockKey = lockKeyForQuery(trimmed);
        ReentrantLock localLock = QUERY_LOCKS.computeIfAbsent(lockKey, k -> new ReentrantLock());

        localLock.lock();
        try {
            Optional<AdminMusicIngestService.IngestedMusic> existing = findLocalMatch(trimmed);
            if (existing.isPresent()) {
                return new FillAttempt(existing, FillReason.NONE);
            }

            boolean redisHeld = tryAcquireRedisLock(lockKey);
            if (!redisHeld) {
                logger.info("网易云补全已有其它实例/请求在处理: query={}", trimmed);
                return waitForPeerIngest(trimmed);
            }

            try {
                existing = findLocalMatch(trimmed);
                if (existing.isPresent()) {
                    return new FillAttempt(existing, FillReason.NONE);
                }
                return doFillFromNetease(trimmed);
            } finally {
                releaseRedisLock(lockKey);
            }
        } catch (SQLException e) {
            logger.error("查询本地曲库失败 query={}", trimmed, e);
            return new FillAttempt(Optional.empty(), FillReason.ERROR);
        } finally {
            localLock.unlock();
        }
    }

    private FillAttempt doFillFromNetease(String trimmed) {
        boolean loggedIn = neteaseClient.isLoggedIn();
        if (!loggedIn) {
            logger.warn("网易云 API 未登录或 Cookie 已失效（/login/status profile 为空），"
                    + "Hi-Res/无损可能不可用，将尝试降档或跳过");
        }

        Path workDir = null;
        try {
            List<NeteaseCloudMusicClient.NeteaseSongCandidate> candidates =
                    neteaseClient.searchSongs(trimmed, 8);
            if (candidates.isEmpty()) {
                logger.info("网易云搜索无结果: query={}", trimmed);
                return failReason(loggedIn);
            }

            candidates.sort(Comparator.comparingInt(c -> scoreCandidate(c, trimmed)));

            workDir = Files.createTempDirectory("neko-netease-fill-");
            for (NeteaseCloudMusicClient.NeteaseSongCandidate candidate : candidates) {
                String album = candidate.album() == null || candidate.album().isBlank()
                        ? "未知专辑"
                        : candidate.album();
                if (ingestService.isDuplicateMusic(candidate.title(), candidate.artist(), album)) {
                    Optional<AdminMusicIngestService.IngestedMusic> dup =
                            findLocalMatch(candidate.title());
                    if (dup.isPresent()) {
                        return new FillAttempt(dup, FillReason.NONE);
                    }
                    continue;
                }
                Optional<AdminMusicIngestService.IngestedMusic> ingested =
                        downloadAndIngest(candidate, workDir);
                if (ingested.isPresent()) {
                    return new FillAttempt(ingested, FillReason.NONE);
                }
            }
            logger.info("网易云候选均未能入库: query={}", trimmed);
            return failReason(loggedIn);
        } catch (Exception e) {
            logger.error("网易云搜索补全失败 query={}", trimmed, e);
            return new FillAttempt(Optional.empty(), FillReason.ERROR);
        } finally {
            if (workDir != null) {
                deleteRecursively(workDir);
            }
        }
    }

    private FillAttempt waitForPeerIngest(String query) {
        long deadline = System.currentTimeMillis() + PEER_WAIT_MAX_MS;
        while (System.currentTimeMillis() < deadline) {
            try {
                Optional<AdminMusicIngestService.IngestedMusic> existing = findLocalMatch(query);
                if (existing.isPresent()) {
                    logger.info("等待其它补全完成后命中曲库: query={} id={}", query, existing.get().id());
                    return new FillAttempt(existing, FillReason.NONE);
                }
                Thread.sleep(PEER_WAIT_POLL_MS);
            } catch (SQLException e) {
                logger.error("等待补全时查询曲库失败 query={}", query, e);
                return new FillAttempt(Optional.empty(), FillReason.ERROR);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return FillAttempt.skipped();
            }
        }
        logger.warn("等待其它补全超时: query={}", query);
        return FillAttempt.skipped();
    }

    private Optional<AdminMusicIngestService.IngestedMusic> findLocalMatch(String query) throws SQLException {
        return ingestService.findBestLocalMatchForQuery(query);
    }

    private boolean tryAcquireRedisLock(String lockKey) {
        if (redisService == null) {
            return true;
        }
        return redisService.setIfAbsentWithExpiry(redisLockKey(lockKey), "1", REDIS_LOCK_SECONDS);
    }

    private void releaseRedisLock(String lockKey) {
        if (redisService == null) {
            return;
        }
        redisService.del(redisLockKey(lockKey));
    }

    private static String redisLockKey(String lockKey) {
        return "neko:netease_fill:lock:" + lockKey;
    }

    private static String lockKeyForQuery(String query) {
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return normalized;
        }
    }

    private Optional<AdminMusicIngestService.IngestedMusic> downloadAndIngest(
            NeteaseCloudMusicClient.NeteaseSongCandidate candidate,
            Path workDir
    ) throws IOException, SQLException {
        long songId = candidate.id();
        NeteaseCloudMusicClient.SongDetail detail = neteaseClient.fetchSongDetail(songId)
                .orElse(new NeteaseCloudMusicClient.SongDetail(
                        candidate.title(),
                        candidate.artist(),
                        candidate.album(),
                        "",
                        0
                ));

        String title = pickNonEmpty(detail.title(), candidate.title());
        String artist = pickNonEmpty(detail.artist(), candidate.artist());
        String album = pickNonEmpty(detail.album(), candidate.album());
        if (album.isBlank()) {
            album = "未知专辑";
        }

        if (ingestService.isDuplicateMusic(title, artist, album)) {
            return ingestService.findBestLocalMatchForQuery(title);
        }

        NeteaseCloudMusicClient.SongPlayUrl playUrl = neteaseClient
                .resolvePlayUrl(songId, config.getNeteaseQuality())
                .orElse(null);
        if (playUrl == null || playUrl.url().isBlank()) {
            logger.warn("无法获取播放链接 songId={} title={}", songId, title);
            return Optional.empty();
        }

        String audioExt = normalizeAudioExt(playUrl.type());
        Path audioTemp = workDir.resolve("audio." + audioExt);
        neteaseClient.downloadToFile(playUrl.url(), audioTemp);

        LyricsPrepareResult lyricsPrep = prepareLyricsFile(workDir, songId, title, artist);

        Path coverTemp = null;
        if (detail.coverUrl() != null && !detail.coverUrl().isBlank()) {
            coverTemp = workDir.resolve("cover.jpg");
            try {
                neteaseClient.downloadToFile(detail.coverUrl(), coverTemp);
            } catch (IOException e) {
                logger.warn("封面下载失败 songId={}: {}", songId, e.getMessage());
                Files.deleteIfExists(coverTemp);
                coverTemp = null;
            }
        }

        int durationSec = detail.durationMs() > 0
                ? detail.durationMs() / 1000
                : (playUrl.durationMs() > 0 ? playUrl.durationMs() / 1000 : 0);

        String language = resolveLanguage(title, artist, album, lyricsPrep.lyricsPath());
        logger.info("网易云补全语种: title={} language={}", title, language);

        Optional<AdminMusicIngestService.IngestedMusic> ingested = ingestService.ingestFromTempFiles(
                audioTemp,
                coverTemp,
                lyricsPrep.lyricsPath(),
                title,
                artist,
                album,
                language,
                "",
                durationSec,
                config.getNeteaseFillUploadUserId()
        );
        if (ingested.isPresent()) {
            lyricsPrep.invalidLyricsAlert().ifPresent(alert ->
                    Main.getEmailService().scheduleNeteaseInvalidLyricsAlertToAdmins(
                            alert.neteaseSongId(),
                            ingested.get().id(),
                            alert.title(),
                            alert.artist(),
                            alert.fullOriginalLyrics(),
                            alert.reason()));
        }
        return ingested;
    }

    private record InvalidLyricsAlert(
            long neteaseSongId, String title, String artist, String fullOriginalLyrics, String reason) {
    }

    private record LyricsPrepareResult(Path lyricsPath, Optional<InvalidLyricsAlert> invalidLyricsAlert) {
    }

    private String resolveLanguage(String title, String artist, String album, Path lyricsTemp) {
        String configured = config.getNeteaseFillLanguage();
        if (configured != null && !configured.isBlank()) {
            String c = configured.trim();
            if (!"auto".equalsIgnoreCase(c)) {
                return c;
            }
        }
        String lrcSample = "";
        try {
            if (Files.isRegularFile(lyricsTemp)) {
                lrcSample = Files.readString(lyricsTemp);
                if (lrcSample.length() > 4000) {
                    lrcSample = lrcSample.substring(0, 4000);
                }
            }
        } catch (IOException e) {
            logger.warn("读取歌词样本用于语种推断失败: {}", e.getMessage());
        }
        return SongLanguageInferer.infer(title, artist, album, lrcSample);
    }

    private LyricsPrepareResult prepareLyricsFile(Path workDir, long songId, String title, String artist) throws IOException {
        Path lyricsTemp = workDir.resolve("lyrics.lrc");
        Optional<InvalidLyricsAlert> invalidAlert = Optional.empty();
        String lrc = "";
        String fullOriginalLyrics = "";
        try {
            NeteaseCloudMusicClient.LyricApiPayload payload = neteaseClient.fetchLyricPayload(songId);
            lrc = payload.primaryLrc();
            fullOriginalLyrics = payload.fullOriginalLyrics();
        } catch (IOException e) {
            logger.warn("获取歌词失败 songId={}: {}", songId, e.getMessage());
        }

        if (!lrc.isBlank()) {
            Files.writeString(lyricsTemp, lrc);
            try (InputStream in = Files.newInputStream(lyricsTemp)) {
                long size = Files.size(lyricsTemp);
                LrcValidator.ValidationResult check = LrcValidator.validate(in, size);
                if (check.isValid()) {
                    return new LyricsPrepareResult(lyricsTemp, Optional.empty());
                }
                String reason = check.getErrorMessage();
                String lyricsForEmail = !fullOriginalLyrics.isBlank() ? fullOriginalLyrics : lrc;
                logger.warn("网易云歌词未通过 LRC 校验 songId={} title={}: {}，将使用占位歌词",
                        songId, title, reason);
                logger.warn("网易云完整原始歌词 songId={}:\n{}", songId, lyricsForEmail);
                invalidAlert = Optional.of(new InvalidLyricsAlert(songId, title, artist, lyricsForEmail, reason));
            }
            Files.deleteIfExists(lyricsTemp);
        }

        writePlaceholderLyrics(lyricsTemp);
        return new LyricsPrepareResult(lyricsTemp, invalidAlert);
    }

    private void writePlaceholderLyrics(Path lyricsTemp) throws IOException {
        try (InputStream fallback = getClass().getClassLoader().getResourceAsStream("no_lrc.lrc")) {
            if (fallback == null) {
                Files.writeString(lyricsTemp, "[00:00.00]Neko云音乐 暂无歌词\n");
            } else {
                Files.copy(fallback, lyricsTemp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private FillAttempt failReason(boolean loggedIn) {
        if (!loggedIn && prefersHighQuality(config.getNeteaseQuality())) {
            return new FillAttempt(Optional.empty(), FillReason.LOGIN_EXPIRED);
        }
        return new FillAttempt(Optional.empty(), FillReason.NOT_FOUND);
    }

    private static boolean prefersHighQuality(String quality) {
        if (quality == null || quality.isBlank()) {
            return true;
        }
        String q = quality.trim().toLowerCase(Locale.ROOT);
        return switch (q) {
            case "hires", "lossless", "jymaster", "jyeffect", "sky" -> true;
            default -> false;
        };
    }

    private static int scoreCandidate(NeteaseCloudMusicClient.NeteaseSongCandidate c, String query) {
        String q = query.toLowerCase(Locale.ROOT);
        String title = c.title().toLowerCase(Locale.ROOT);
        String artist = c.artist().toLowerCase(Locale.ROOT);
        if (title.equals(q)) {
            return 0;
        }
        if (title.startsWith(q)) {
            return 1;
        }
        if (title.contains(q) || artist.contains(q)) {
            return 2;
        }
        return 3;
    }

    private static String pickNonEmpty(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        return fallback == null ? "" : fallback.trim();
    }

    private static String normalizeAudioExt(String type) {
        if (type == null) {
            return "mp3";
        }
        String t = type.trim().toLowerCase(Locale.ROOT);
        return switch (t) {
            case "flac" -> "flac";
            case "wav" -> "wav";
            default -> "mp3";
        };
    }

    private static void deleteRecursively(Path root) {
        try {
            if (!Files.exists(root)) {
                return;
            }
            try (var walk = Files.walk(root)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                    }
                });
            }
        } catch (IOException e) {
            logger.warn("清理临时目录失败: {}", root, e);
        }
    }
}
