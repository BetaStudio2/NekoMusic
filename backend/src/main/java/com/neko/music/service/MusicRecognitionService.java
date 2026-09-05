package com.neko.music.service;

import com.neko.music.config.ConfigManager;
import com.neko.music.database.DatabaseManager;
import com.neko.music.util.BundledFfmpegSupport;
import com.neko.music.util.MusicAssetLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Builds and queries the self-hosted music library's acoustic fingerprint index.
 * No recognition data leaves the server.
 */
public final class MusicRecognitionService implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(MusicRecognitionService.class);

    private final ConfigManager config;
    private final TrackCatalog catalog;
    private final FingerprintDecoder decoder;
    private final FingerprintDiskCache diskCache;
    private final ExecutorService indexExecutor;
    private final Semaphore querySlots;
    private final Object buildLock = new Object();

    private volatile IndexSnapshot currentIndex;
    private volatile CompletableFuture<IndexSnapshot> indexBuild;

    public MusicRecognitionService(DatabaseManager databaseManager, ConfigManager config) {
        this(
                config,
                new DatabaseTrackCatalog(databaseManager),
                new FfmpegFingerprintDecoder(config, new AudioFingerprintEngine()),
                new FingerprintDiskCache(MusicAssetLocator.baseDir().resolve("Music/.fingerprints")));
    }

    MusicRecognitionService(
            ConfigManager config,
            TrackCatalog catalog,
            FingerprintDecoder decoder,
            FingerprintDiskCache diskCache) {
        this.config = config;
        this.catalog = catalog;
        this.decoder = decoder;
        this.diskCache = diskCache;
        this.querySlots = new Semaphore(config.getMusicRecognitionMaxConcurrentRequests(), true);
        this.indexExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "music-fingerprint-index");
            thread.setDaemon(true);
            return thread;
        });
    }

    public Optional<RecognitionResult> recognize(Path uploadedAudio)
            throws IOException, InvalidAudioException, AudioTooLongException,
            BusyException, IndexUnavailableException {
        if (!querySlots.tryAcquire()) {
            throw new BusyException("识曲任务繁忙，请稍后重试");
        }
        try {
            IndexSnapshot snapshot = getUsableIndex();
            AudioFingerprintEngine.Fingerprint query = decoder.decode(
                    uploadedAudio,
                    config.getMusicRecognitionMaxSampleDurationSeconds(),
                    Duration.ofSeconds(config.getMusicRecognitionFfmpegTimeoutSeconds()));
            if (query.durationSeconds() < config.getMusicRecognitionMinSampleDurationSeconds()) {
                throw new InvalidAudioException("录音过短，请至少录制 "
                        + config.getMusicRecognitionMinSampleDurationSeconds() + " 秒");
            }
            if (query.landmarks().isEmpty()) {
                throw new InvalidAudioException("录音中没有足够清晰的声音");
            }

            Optional<AudioFingerprintEngine.Match> match = snapshot.index.findBest(
                    query,
                    config.getMusicRecognitionMinimumMatchingLandmarks(),
                    config.getMusicRecognitionMinimumConfidence());
            if (match.isEmpty()) {
                return Optional.empty();
            }
            Track track = snapshot.tracks.get(match.get().musicId());
            if (track == null) {
                return Optional.empty();
            }
            AudioFingerprintEngine.Match value = match.get();
            return Optional.of(new RecognitionResult(
                    track,
                    value.confidence(),
                    value.alignedLandmarks(),
                    Math.max(0d, value.offsetSeconds()),
                    query.durationSeconds()));
        } finally {
            querySlots.release();
        }
    }

    public IndexStatus status() {
        IndexSnapshot snapshot = currentIndex;
        CompletableFuture<IndexSnapshot> build = indexBuild;
        return new IndexStatus(
                snapshot != null,
                build != null && !build.isDone(),
                snapshot == null ? 0 : snapshot.index.musicCount(),
                snapshot == null ? 0 : snapshot.index.uniqueHashCount(),
                snapshot == null ? 0 : snapshot.builtAtMillis);
    }

    /** Useful after an administrative ingest; the next request rebuilds from per-song caches. */
    public void invalidateIndex() {
        currentIndex = null;
    }

    private IndexSnapshot getUsableIndex() throws IndexUnavailableException {
        IndexSnapshot ready = currentIndex;
        if (ready != null) {
            long ageMillis = System.currentTimeMillis() - ready.builtAtMillis;
            if (ageMillis >= TimeUnit.SECONDS.toMillis(config.getMusicRecognitionIndexRefreshSeconds())) {
                startBuildIfNeeded();
            }
            return ready;
        }

        CompletableFuture<IndexSnapshot> build = startBuildIfNeeded();
        try {
            return build.get(config.getMusicRecognitionIndexBuildWaitSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new IndexUnavailableException("曲库声纹索引正在构建，请稍后重试", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IndexUnavailableException("等待曲库声纹索引时被中断", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IndexUnavailableException("曲库声纹索引构建失败: " + safeMessage(cause), cause);
        }
    }

    private CompletableFuture<IndexSnapshot> startBuildIfNeeded() {
        CompletableFuture<IndexSnapshot> existing = indexBuild;
        if (existing != null && !existing.isDone()) {
            return existing;
        }
        synchronized (buildLock) {
            existing = indexBuild;
            if (existing != null && !existing.isDone()) {
                return existing;
            }
            CompletableFuture<IndexSnapshot> created = CompletableFuture.supplyAsync(() -> {
                try {
                    return buildIndex();
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }, indexExecutor);
            indexBuild = created;
            created.whenComplete((snapshot, error) -> {
                synchronized (buildLock) {
                    if (error == null && snapshot != null) {
                        currentIndex = snapshot;
                    }
                    if (indexBuild == created) {
                        indexBuild = null;
                    }
                }
                if (error != null) {
                    logger.error("曲库声纹索引构建失败", unwrapCompletion(error));
                }
            });
            return created;
        }
    }

    private IndexSnapshot buildIndex() throws Exception {
        long started = System.currentTimeMillis();
        List<Track> catalogTracks = catalog.loadTracks();
        Map<Integer, Track> indexedTracks = new LinkedHashMap<>();
        Map<Integer, AudioFingerprintEngine.Fingerprint> fingerprints = new HashMap<>();
        int cacheHits = 0;
        int generated = 0;
        int audioFiles = 0;
        Exception lastFailure = null;

        logger.info("开始构建曲库声纹索引，数据库歌曲数={}", catalogTracks.size());
        for (Track track : catalogTracks) {
            Optional<Path> audioFile = MusicAssetLocator.findAudioFile(track.id());
            if (audioFile.isEmpty()) {
                continue;
            }
            audioFiles++;
            Path audio = audioFile.get();
            try {
                Optional<AudioFingerprintEngine.Fingerprint> cached = diskCache.load(track.id(), audio);
                AudioFingerprintEngine.Fingerprint fingerprint;
                if (cached.isPresent()) {
                    fingerprint = cached.get();
                    cacheHits++;
                } else {
                    fingerprint = decoder.decode(
                            audio,
                            config.getMusicRecognitionIndexMaxTrackDurationSeconds(),
                            Duration.ofSeconds(config.getMusicRecognitionIndexFfmpegTimeoutSeconds()));
                    diskCache.save(track.id(), audio, fingerprint);
                    generated++;
                }
                if (!fingerprint.landmarks().isEmpty()) {
                    fingerprints.put(track.id(), fingerprint);
                    indexedTracks.put(track.id(), track);
                } else {
                    logger.warn("歌曲无法生成有效声纹，已跳过 musicId={} path={}", track.id(), audio);
                }
            } catch (Exception e) {
                lastFailure = e;
                logger.warn("生成歌曲声纹失败，已跳过 musicId={} path={}: {}",
                        track.id(), audio, safeMessage(e));
            }
        }
        if (audioFiles > 0 && fingerprints.isEmpty() && lastFailure != null) {
            throw new IOException("没有任何曲库音频成功生成声纹", lastFailure);
        }

        AudioFingerprintEngine.Index index = AudioFingerprintEngine.Index.build(fingerprints);
        IndexSnapshot snapshot = new IndexSnapshot(index, Map.copyOf(indexedTracks), System.currentTimeMillis());
        logger.info("曲库声纹索引构建完成: indexedMusic={}, uniqueHashes={}, cacheHits={}, generated={}, elapsedMs={}",
                index.musicCount(), index.uniqueHashCount(), cacheHits, generated,
                System.currentTimeMillis() - started);
        return snapshot;
    }

    @Override
    public void close() {
        indexExecutor.shutdownNow();
    }

    public record Track(
            int id,
            String title,
            String artist,
            String album,
            int duration,
            String language,
            String tags,
            String updatedAt) {
    }

    public record RecognitionResult(
            Track track,
            double confidence,
            int matchedLandmarks,
            double offsetSeconds,
            double sampleDurationSeconds) {
    }

    public record IndexStatus(
            boolean ready,
            boolean building,
            int indexedMusic,
            int uniqueHashes,
            long builtAtMillis) {
    }

    interface TrackCatalog {
        List<Track> loadTracks() throws Exception;
    }

    interface FingerprintDecoder {
        AudioFingerprintEngine.Fingerprint decode(Path audio, int maxDurationSeconds, Duration timeout)
                throws IOException, InvalidAudioException, AudioTooLongException;
    }

    private record IndexSnapshot(
            AudioFingerprintEngine.Index index,
            Map<Integer, Track> tracks,
            long builtAtMillis) {
    }

    private static final class DatabaseTrackCatalog implements TrackCatalog {
        private final DatabaseManager databaseManager;

        private DatabaseTrackCatalog(DatabaseManager databaseManager) {
            this.databaseManager = databaseManager;
        }

        @Override
        public List<Track> loadTracks() throws Exception {
            String sql = "SELECT id, title, artist, album, duration, language, tags, updated_at "
                    + "FROM music ORDER BY id";
            List<Track> tracks = new ArrayList<>();
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    tracks.add(new Track(
                            result.getInt("id"),
                            nullToEmpty(result.getString("title")),
                            nullToEmpty(result.getString("artist")),
                            nullToEmpty(result.getString("album")),
                            result.getInt("duration"),
                            nullToEmpty(result.getString("language")),
                            nullToEmpty(result.getString("tags")),
                            result.getTimestamp("updated_at") == null
                                    ? ""
                                    : result.getTimestamp("updated_at").toInstant().toString()));
                }
            }
            return tracks;
        }
    }

    private static final class FfmpegFingerprintDecoder implements FingerprintDecoder {
        private final ConfigManager config;
        private final AudioFingerprintEngine engine;

        private FfmpegFingerprintDecoder(ConfigManager config, AudioFingerprintEngine engine) {
            this.config = config;
            this.engine = engine;
        }

        @Override
        public AudioFingerprintEngine.Fingerprint decode(Path audio, int maxDurationSeconds, Duration timeout)
                throws IOException, InvalidAudioException, AudioTooLongException {
            String ffmpeg = BundledFfmpegSupport.resolve(
                    config.getVideoRenderFfmpegPath(),
                    config.isVideoRenderPreferBundledFfmpeg());
            List<String> command = List.of(
                    ffmpeg,
                    "-hide_banner",
                    "-loglevel", "error",
                    "-nostdin",
                    "-i", audio.toAbsolutePath().normalize().toString(),
                    "-map", "0:a:0",
                    "-vn",
                    "-sn",
                    "-dn",
                    "-ac", "1",
                    "-ar", String.valueOf(AudioFingerprintEngine.SAMPLE_RATE),
                    "-t", String.valueOf(maxDurationSeconds + 1L),
                    "-f", "s16le",
                    "-acodec", "pcm_s16le",
                    "pipe:1");
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process process = processBuilder.start();
            CompletableFuture<Void> killer = CompletableFuture.runAsync(() -> {
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            }, CompletableFuture.delayedExecutor(Math.max(1L, timeout.toSeconds()), TimeUnit.SECONDS));

            try (InputStream stdout = process.getInputStream()) {
                long maxSamples = (long) maxDurationSeconds * AudioFingerprintEngine.SAMPLE_RATE;
                AudioFingerprintEngine.Fingerprint fingerprint;
                try {
                    fingerprint = engine.fingerprint(stdout, maxSamples);
                } catch (AudioFingerprintEngine.SampleLimitExceededException e) {
                    process.destroyForcibly();
                    throw new AudioTooLongException("音频时长不得超过 " + maxDurationSeconds + " 秒", e);
                }

                boolean exited;
                try {
                    exited = process.waitFor(Math.max(1L, timeout.toSeconds()), TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    process.destroyForcibly();
                    throw new IOException("等待 FFmpeg 解码时被中断", e);
                }
                if (!exited) {
                    process.destroyForcibly();
                    throw new IOException("FFmpeg 解码超时");
                }
                if (process.exitValue() != 0) {
                    throw new InvalidAudioException("无法解码上传的音频");
                }
                return fingerprint;
            } finally {
                killer.cancel(false);
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            }
        }
    }

    static final class FingerprintDiskCache {
        private static final int MAGIC = 0x4e465031; // NFP1
        private static final int MAX_CACHED_LANDMARKS = 2_000_000;

        private final Path directory;

        FingerprintDiskCache(Path directory) {
            this.directory = directory;
        }

        Optional<AudioFingerprintEngine.Fingerprint> load(int musicId, Path audio) {
            Path cacheFile = cacheFile(musicId);
            if (!Files.isRegularFile(cacheFile)) {
                return Optional.empty();
            }
            try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(cacheFile)))) {
                if (input.readInt() != MAGIC
                        || input.readInt() != AudioFingerprintEngine.ALGORITHM_VERSION
                        || input.readInt() != musicId
                        || input.readLong() != Files.size(audio)
                        || input.readLong() != Files.getLastModifiedTime(audio).toMillis()
                        || !input.readUTF().equals(audio.getFileName().toString())) {
                    return Optional.empty();
                }
                long sampleCount = input.readLong();
                int count = input.readInt();
                if (sampleCount < 0 || count < 0 || count > MAX_CACHED_LANDMARKS) {
                    return Optional.empty();
                }
                List<AudioFingerprintEngine.Landmark> landmarks = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    landmarks.add(new AudioFingerprintEngine.Landmark(input.readInt(), input.readInt()));
                }
                return Optional.of(new AudioFingerprintEngine.Fingerprint(landmarks, sampleCount));
            } catch (Exception e) {
                logger.debug("读取声纹缓存失败 musicId={} cache={}: {}", musicId, cacheFile, safeMessage(e));
                return Optional.empty();
            }
        }

        void save(int musicId, Path audio, AudioFingerprintEngine.Fingerprint fingerprint) throws IOException {
            Files.createDirectories(directory);
            Path target = cacheFile(musicId);
            Path temp = Files.createTempFile(directory, musicId + "-", ".tmp");
            try {
                try (DataOutputStream output = new DataOutputStream(
                        new BufferedOutputStream(Files.newOutputStream(temp)))) {
                    output.writeInt(MAGIC);
                    output.writeInt(AudioFingerprintEngine.ALGORITHM_VERSION);
                    output.writeInt(musicId);
                    output.writeLong(Files.size(audio));
                    output.writeLong(Files.getLastModifiedTime(audio).toMillis());
                    output.writeUTF(audio.getFileName().toString());
                    output.writeLong(fingerprint.sampleCount());
                    output.writeInt(fingerprint.landmarks().size());
                    for (AudioFingerprintEngine.Landmark landmark : fingerprint.landmarks()) {
                        output.writeInt(landmark.hash());
                        output.writeInt(landmark.timeFrame());
                    }
                }
                try {
                    Files.move(temp, target,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        }

        private Path cacheFile(int musicId) {
            return directory.resolve(musicId + ".nfp");
        }
    }

    public static final class InvalidAudioException extends Exception {
        public InvalidAudioException(String message) {
            super(message);
        }
    }

    public static final class AudioTooLongException extends Exception {
        public AudioTooLongException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class BusyException extends Exception {
        public BusyException(String message) {
            super(message);
        }
    }

    public static final class IndexUnavailableException extends Exception {
        public IndexUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private static Throwable unwrapCompletion(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
