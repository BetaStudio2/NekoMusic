package com.neko.music.service;

import com.neko.music.config.ConfigManager;
import com.neko.music.util.LrcValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 本地单曲搜索无结果时：从 NeteaseCloudMusicApi 拉取一首匹配曲并走管理员入库流程。
 */
public class NeteaseSearchFillService {
    private static final Logger logger = LoggerFactory.getLogger(NeteaseSearchFillService.class);

    private final ConfigManager config;
    private final NeteaseCloudMusicClient neteaseClient;
    private final AdminMusicIngestService ingestService;

    public NeteaseSearchFillService(
            ConfigManager config,
            NeteaseCloudMusicClient neteaseClient,
            AdminMusicIngestService ingestService
    ) {
        this.config = config;
        this.neteaseClient = neteaseClient;
        this.ingestService = ingestService;
    }

    public enum FillReason {
        /** 补全成功或未触发补全 */
        NONE,
        /** 网易云无匹配或无法取链/入库 */
        NOT_FOUND,
        /** API 未登录，高音质不可用（常因 Cookie 过期） */
        LOGIN_EXPIRED,
        /** 其它异常 */
        ERROR
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
        if (config.getNeteaseApiBaseUrl().isBlank()) {
            logger.warn("netease_search_fill 已启用但 api_base_url 为空");
            return new FillAttempt(Optional.empty(), FillReason.ERROR);
        }

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

        Path lyricsTemp = prepareLyricsFile(workDir, songId);

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

        return ingestService.ingestFromTempFiles(
                audioTemp,
                coverTemp,
                lyricsTemp,
                title,
                artist,
                album,
                config.getNeteaseFillLanguage(),
                "",
                durationSec,
                config.getNeteaseFillUploadUserId()
        );
    }

    private Path prepareLyricsFile(Path workDir, long songId) throws IOException {
        Path lyricsTemp = workDir.resolve("lyrics.lrc");
        String lrc = "";
        try {
            lrc = neteaseClient.fetchLyricLrc(songId);
        } catch (IOException e) {
            logger.warn("获取歌词失败 songId={}: {}", songId, e.getMessage());
        }

        if (!lrc.isBlank()) {
            Files.writeString(lyricsTemp, lrc);
            try (InputStream in = Files.newInputStream(lyricsTemp)) {
                long size = Files.size(lyricsTemp);
                if (LrcValidator.validate(in, size).isValid()) {
                    return lyricsTemp;
                }
            }
            Files.deleteIfExists(lyricsTemp);
        }

        try (InputStream fallback = getClass().getClassLoader().getResourceAsStream("no_lrc.lrc")) {
            if (fallback == null) {
                Files.writeString(lyricsTemp, "[00:00.00]暂无歌词\n");
            } else {
                Files.copy(fallback, lyricsTemp);
            }
        }
        return lyricsTemp;
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
