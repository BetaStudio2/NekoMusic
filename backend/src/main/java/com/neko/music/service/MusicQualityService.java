package com.neko.music.service;

import com.neko.music.Main;
import com.neko.music.nativeaudio.NativeAudioQuality;
import com.neko.music.util.BundledFfmpegSupport;
import com.neko.music.util.MusicAssetLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

public final class MusicQualityService {
    public static final String STANDARD = "standard";
    public static final String HQ = "hq";
    public static final String SQ = "sq";
    public static final String HIRES = "hires";

    private static final Logger logger = LoggerFactory.getLogger(MusicQualityService.class);
    private static final ConcurrentMap<String, ReentrantLock> TRANSCODE_LOCKS = new ConcurrentHashMap<>();
    private static final int STANDARD_KBPS = 128;
    private static final int HQ_KBPS = 320;

    private MusicQualityService() {
    }

    public static void refreshDatabase(int musicId, Path source) throws SQLException, IOException {
        Probe probe = probe(source);
        try (Connection conn = Main.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement("UPDATE music SET max_quality = ?, bitrate_bps = ?, sample_rate_hz = ?, bits_per_sample = ?, channels = ?, updated_at = NOW() WHERE id = ?")) {
            stmt.setString(1, probe.quality());
            stmt.setInt(2, probe.bitrateBps());
            stmt.setInt(3, probe.sampleRateHz());
            stmt.setInt(4, probe.bitsPerSample());
            stmt.setInt(5, probe.channels());
            stmt.setInt(6, musicId);
            stmt.executeUpdate();
        }
    }

    public static String resolveAudio(int musicId, String requested) throws IOException, SQLException {
        Path source = MusicAssetLocator.findAudioFile(musicId).orElseThrow(() -> new IOException("音乐文件不存在"));
        String maxQuality = loadMaxQuality(musicId, source);
        String requestedQuality = normalize(requested);
        int requestedRank = qualityRank(requestedQuality);
        int maxRank = qualityRank(maxQuality);
        if (requestedRank >= maxRank) {
            return source.toString();
        }
        String actual = requestedQuality;

        if (qualityRank(actual) >= qualityRank(SQ)) {
            return source.toString();
        }

        Path derived = derivedPath(musicId, actual);
        if (Files.isRegularFile(derived) && Files.size(derived) > 0) {
            return derived.toString();
        }

        ReentrantLock lock = TRANSCODE_LOCKS.computeIfAbsent(derived.toString(), ignored -> new ReentrantLock());
        lock.lock();
        try {
            if (Files.isRegularFile(derived) && Files.size(derived) > 0) {
                return derived.toString();
            }
            Files.createDirectories(derived.getParent());
            Path temporary = Files.createTempFile(derived.getParent(), musicId + "-" + actual + "-", ".tmp");
            try {
                int bitrate = STANDARD.equals(actual) ? STANDARD_KBPS : HQ_KBPS;
                int code = NativeAudioQuality.transcode(resolveFfmpeg(), source.toString(), temporary.toString(), bitrate);
                if (code != 0 || !Files.isRegularFile(temporary) || Files.size(temporary) == 0) {
                    throw new IOException("音频压缩失败，退出码: " + code);
                }
                Files.move(temporary, derived, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                return derived.toString();
            } finally {
                Files.deleteIfExists(temporary);
            }
        } finally {
            lock.unlock();
            TRANSCODE_LOCKS.remove(derived.toString(), lock);
        }
    }

    public static String publicUrl(int musicId, String absolutePath) {
        Path path = Path.of(absolutePath).toAbsolutePath().normalize();
        Path derivedDir = MusicAssetLocator.derivedDir(musicId).toAbsolutePath().normalize();
        if (path.startsWith(derivedDir)) {
            String name = path.getFileName().toString();
            return "/media/music/" + musicId + "/" + name;
        }
        return "/media/music/" + musicId + "/" + path.getFileName();
    }

    public static Probe probe(Path source) throws IOException {
        if (!NativeAudioQuality.isAvailable()) {
            throw new IOException("未加载音质 JNI 库，请先构建 backend/native/libneko_audio_quality.so");
        }
        long[] values = NativeAudioQuality.probe(source.toAbsolutePath().toString());
        if (values == null || values.length < 5 || values[0] < 0) {
            throw new IOException("无法解析音频参数: " + source);
        }
        return new Probe(qualityFromNative(values[0]), (int) values[1], (int) values[2], (int) values[3], (int) values[4]);
    }

    private static String loadMaxQuality(int musicId, Path source) throws IOException, SQLException {
        try (Connection conn = Main.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT max_quality FROM music WHERE id = ?")) {
            stmt.setInt(1, musicId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getString(1) != null && !rs.getString(1).isBlank()) {
                    return normalize(rs.getString(1));
                }
            }
        }
        Probe probe = probe(source);
        refreshDatabase(musicId, source);
        return probe.quality();
    }

    private static Path derivedPath(int musicId, String quality) {
        return MusicAssetLocator.derivedDir(musicId).resolve(quality + ".mp3");
    }

    private static String resolveFfmpeg() throws IOException {
        String configured = System.getProperty("neko.ffmpeg");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return BundledFfmpegSupport.resolve(
                Main.getConfigManager().getVideoRenderFfmpegPath(),
                Main.getConfigManager().isVideoRenderPreferBundledFfmpeg());
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) return HQ;
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case STANDARD, "lq", "128" -> STANDARD;
            case HQ, "mq", "320" -> HQ;
            case SQ, "lossless", "无损" -> SQ;
            case HIRES, "hi-res", "hi_res" -> HIRES;
            default -> HQ;
        };
    }

    public static int qualityRank(String quality) {
        return switch (normalize(quality)) {
            case STANDARD -> 0;
            case HQ -> 1;
            case SQ -> 2;
            case HIRES -> 3;
            default -> 1;
        };
    }

    private static String qualityFromNative(long tier) {
        return switch ((int) tier) {
            case 1 -> STANDARD;
            case 2 -> HQ;
            case 3 -> SQ;
            case 4 -> HIRES;
            default -> HQ;
        };
    }

    public record Probe(String quality, int bitrateBps, int sampleRateHz, int bitsPerSample, int channels) {
    }
}
