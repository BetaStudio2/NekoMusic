package com.neko.music.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 外部歌单导入：把 QQ / 网易云歌单的曲目入库并加入用户指定的歌单，SSE 进度由上层推送。
 *
 * <ul>
 *   <li>网易云：按歌曲 ID 直接下载原曲入库（不经过站内搜索匹配）。</li>
 *   <li>QQ：QQ 歌单只提供元数据，因此先在站内曲库匹配，匹配不到再按「歌名 + 歌手」
 *       从网易云补全下载。</li>
 * </ul>
 */
public class ExternalImportService {
    private static final Logger logger = LoggerFactory.getLogger(ExternalImportService.class);

    public static final String SOURCE_NETEASE = "netease";
    public static final String SOURCE_QQ = "qq";

    public static final String STATUS_IMPORTED = "imported";
    public static final String STATUS_EXISTED = "existed";
    public static final String STATUS_FAILED = "failed";

    private final NeteaseCloudMusicClient neteaseClient;
    private final NeteaseSearchFillService fillService;
    private final AdminMusicIngestService ingestService;
    private final PlaylistService playlistService;
    private final QQMusicClient qqMusicClient;

    private final ExecutorService executor;

    public record Track(String sourceId, String title, String artist) {
    }

    public record TrackResult(int index, int total, String source, String sourceId, String title, String artist,
                              String status, Integer musicId, boolean playlistAdded, String message) {
    }

    public record Summary(int total, int imported, int existed, int failed) {
    }

    /** 导入过程中的事件回调，全部在同一个导入线程内顺序触发。 */
    public interface Listener {
        void onStart(String source, int total, int targetPlaylistId, boolean targetPlaylistCreated);

        void onTrackStarted(TrackResult result);

        void onTrackProgress(int index, int total, String sourceId, long bytesRead, long totalBytes);

        void onTrackFinished(TrackResult result);

        void onComplete(Summary summary);

        void onError(String message);
    }

    @FunctionalInterface
    private interface ImportTask {
        void run() throws IOException;
    }

    public ExternalImportService(NeteaseCloudMusicClient neteaseClient,
                                 NeteaseSearchFillService fillService,
                                 AdminMusicIngestService ingestService,
                                 PlaylistService playlistService,
                                 QQMusicClient qqMusicClient) {
        this.neteaseClient = neteaseClient;
        this.fillService = fillService;
        this.ingestService = ingestService;
        this.playlistService = playlistService;
        this.qqMusicClient = qqMusicClient;
        AtomicInteger threadNo = new AtomicInteger();
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "external-import-" + threadNo.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    /** 网易云歌单 / 指定歌曲导入。 */
    public void startNeteaseImport(Long neteasePlaylistId, List<Long> songIds, int targetPlaylistId,
                                   boolean targetPlaylistCreated, int userId, Listener listener) {
        submit(() -> runNeteaseImport(neteasePlaylistId, songIds, targetPlaylistId, targetPlaylistCreated,
                        userId, listener),
                listener);
    }

    /** QQ 歌单导入。 */
    public void startQqImport(String disstid, int targetPlaylistId, boolean targetPlaylistCreated,
                              int userId, Listener listener) {
        submit(() -> runQqImport(disstid, targetPlaylistId, targetPlaylistCreated, userId, listener),
                listener);
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private void submit(ImportTask task, Listener listener) {
        try {
            executor.submit(() -> {
                try {
                    task.run();
                } catch (Exception e) {
                    logger.error("外部歌单导入失败", e);
                    listener.onError("导入失败: " + rootMessage(e));
                }
            });
        } catch (RejectedExecutionException e) {
            listener.onError("导入服务不可用");
        }
    }

    private void runNeteaseImport(Long neteasePlaylistId, List<Long> songIds, int targetPlaylistId,
                                  boolean targetPlaylistCreated, int userId, Listener listener) throws IOException {
        List<Track> tracks = resolveNeteaseTracks(neteasePlaylistId, songIds);
        if (tracks.isEmpty()) {
            listener.onError(neteasePlaylistId != null ? "网易云歌单为空或不可访问" : "没有可导入的歌曲 ID");
            return;
        }

        listener.onStart(SOURCE_NETEASE, tracks.size(), targetPlaylistId, targetPlaylistCreated);
        int imported = 0;
        int existed = 0;
        int failed = 0;
        for (int index = 0; index < tracks.size(); index++) {
            if (Thread.currentThread().isInterrupted()) {
                logger.warn("网易云导入被中断，已处理 {}/{}", index, tracks.size());
                break;
            }
            Track track = tracks.get(index);
            int currentIndex = index;
            listener.onTrackStarted(result(SOURCE_NETEASE, currentIndex, tracks.size(), track,
                    "downloading", null, false, null));

            long songId = Long.parseLong(track.sourceId());
            NeteaseSearchFillService.ExactIngest ingest = fillService.ingestExactFromNetease(
                    songId, userId,
                    (bytesRead, totalBytes) -> listener.onTrackProgress(
                            currentIndex, tracks.size(), track.sourceId(), bytesRead, totalBytes));

            String title = firstNonBlank(ingest.title(), track.title());
            String artist = firstNonBlank(ingest.artist(), track.artist());
            Track resolved = new Track(track.sourceId(), title, artist);
            if (ingest.success()) {
                boolean existedAlready = ingest.alreadyExisted();
                boolean added = addToPlaylist(targetPlaylistId, ingest.music().get().id());
                listener.onTrackFinished(result(SOURCE_NETEASE, currentIndex, tracks.size(), resolved,
                        existedAlready ? STATUS_EXISTED : STATUS_IMPORTED,
                        ingest.music().get().id(), added, null));
                if (existedAlready) {
                    existed++;
                } else {
                    imported++;
                }
            } else {
                listener.onTrackFinished(result(SOURCE_NETEASE, currentIndex, tracks.size(), resolved,
                        STATUS_FAILED, null, false, reasonMessage(ingest.reason())));
                failed++;
            }
        }
        listener.onComplete(new Summary(tracks.size(), imported, existed, failed));
    }

    private void runQqImport(String disstid, int targetPlaylistId, boolean targetPlaylistCreated,
                             int userId, Listener listener) throws IOException {
        QQMusicClient.QqPlaylist playlist = qqMusicClient.fetchPlaylist(disstid);
        List<QQMusicClient.QqTrack> qqTracks = playlist.tracks();
        if (qqTracks.isEmpty()) {
            listener.onError("QQ 歌单为空或不可访问");
            return;
        }

        listener.onStart(SOURCE_QQ, qqTracks.size(), targetPlaylistId, targetPlaylistCreated);
        int imported = 0;
        int existed = 0;
        int failed = 0;
        for (int index = 0; index < qqTracks.size(); index++) {
            if (Thread.currentThread().isInterrupted()) {
                logger.warn("QQ 导入被中断，已处理 {}/{}", index, qqTracks.size());
                break;
            }
            QQMusicClient.QqTrack qqTrack = qqTracks.get(index);
            Track track = new Track(qqTrack.mid(), qqTrack.title(), qqTrack.artist());
            listener.onTrackStarted(result(SOURCE_QQ, index, qqTracks.size(), track,
                    "matching", null, false, null));

            Optional<AdminMusicIngestService.IngestedMusic> matched = matchLocal(track.title(), track.artist());
            if (matched.isPresent()) {
                boolean added = addToPlaylist(targetPlaylistId, matched.get().id());
                listener.onTrackFinished(result(SOURCE_QQ, index, qqTracks.size(), track,
                        STATUS_EXISTED, matched.get().id(), added, null));
                existed++;
                continue;
            }

            NeteaseSearchFillService.FillAttempt attempt = fillService.tryFillFromNetease(track.title(), track.artist());
            if (attempt.music().isPresent()) {
                boolean added = addToPlaylist(targetPlaylistId, attempt.music().get().id());
                listener.onTrackFinished(result(SOURCE_QQ, index, qqTracks.size(), track,
                        STATUS_IMPORTED, attempt.music().get().id(), added, null));
                imported++;
            } else {
                listener.onTrackFinished(result(SOURCE_QQ, index, qqTracks.size(), track,
                        STATUS_FAILED, null, false, fillReasonMessage(attempt.reason())));
                failed++;
            }
        }
        listener.onComplete(new Summary(qqTracks.size(), imported, existed, failed));
    }

    private Optional<AdminMusicIngestService.IngestedMusic> matchLocal(String title, String artist) {
        try {
            Optional<AdminMusicIngestService.IngestedMusic> exact =
                    ingestService.findExistingDuplicate(title, artist, "");
            if (exact.isPresent()) {
                return exact;
            }
            return ingestService.findBestLocalMatchForBatchItem(title, artist);
        } catch (SQLException e) {
            logger.warn("站内曲库匹配失败 title={} artist={}: {}", title, artist, e.getMessage());
            return Optional.empty();
        }
    }

    private boolean addToPlaylist(int targetPlaylistId, int musicId) {
        try {
            return playlistService.addMusicToPlaylist(targetPlaylistId, musicId);
        } catch (Exception e) {
            logger.warn("加入歌单失败 playlistId={} musicId={}: {}", targetPlaylistId, musicId, e.getMessage());
            return false;
        }
    }

    /** 解析待导入曲目：歌单模式走网易云歌单数据，IDs 模式直接用给定 ID。 */
    private List<Track> resolveNeteaseTracks(Long neteasePlaylistId, List<Long> songIds) throws IOException {
        List<Track> tracks = new ArrayList<>();
        if (neteasePlaylistId != null) {
            JsonNode response = neteaseClient.fetchPlaylistTrackAll(neteasePlaylistId, 0, 0);
            if (response.path("code").asInt(0) != 200) {
                throw new IOException("网易云歌单不存在或不可访问");
            }
            for (JsonNode song : response.path("songs")) {
                long id = song.path("id").asLong(0);
                if (id <= 0) {
                    continue;
                }
                tracks.add(new Track(Long.toString(id),
                        song.path("name").asText(""), joinArtists(song.path("ar"))));
            }
        } else if (songIds != null) {
            for (Long id : songIds) {
                if (id == null || id <= 0) {
                    continue;
                }
                tracks.add(new Track(Long.toString(id), "", ""));
            }
        }
        return tracks;
    }

    private static TrackResult result(String source, int index, int total, Track track,
                                      String status, Integer musicId, boolean playlistAdded, String message) {
        return new TrackResult(index, total, source, track.sourceId(), track.title(), track.artist(),
                status, musicId, playlistAdded, message);
    }

    private static String joinArtists(JsonNode artists) {
        if (!artists.isArray()) {
            return "";
        }
        List<String> names = new ArrayList<>();
        for (JsonNode artist : artists) {
            String name = artist.path("name").asText("").trim();
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return String.join(" / ", names);
    }

    private static String reasonMessage(NeteaseSearchFillService.FillReason reason) {
        return switch (reason) {
            case LOW_DISK_SPACE -> "服务器磁盘空间不足";
            case LOGIN_EXPIRED -> "网易云 Cookie 失效或该音质不可用";
            case NOT_FOUND -> "网易云无可用音频（可能无版权或需会员）";
            default -> "下载或入库失败";
        };
    }

    private static String fillReasonMessage(NeteaseSearchFillService.FillReason reason) {
        if (reason == NeteaseSearchFillService.FillReason.NONE) {
            return "站内与网易云均未匹配到可下载曲目";
        }
        return reasonMessage(reason);
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback == null ? "" : fallback;
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }
}
