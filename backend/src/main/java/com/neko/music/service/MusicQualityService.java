package com.neko.music.service;

import com.neko.music.Main;
import com.neko.music.nativeaudio.NativeAudioQuality;
import com.neko.music.util.BundledFfmpegSupport;
import com.neko.music.util.MusicAssetLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        String maxQuality;
        try {
            maxQuality = loadMaxQuality(musicId, source);
        } catch (Exception e) {
            // 宁可把原始文件直接给用户，也不要因为解析/写库失败导致无法播放
            logger.warn("音乐音质解析失败 id={}，降级为直接返回源文件", musicId, e);
            return source.toString();
        }
        String requestedQuality = normalize(requested);
        int requestedRank = qualityRank(requestedQuality);
        int maxRank = qualityRank(maxQuality);
        if (requestedRank >= maxRank) {
            return source.toString();
        }
        String actual = requestedQuality;

        if (SQ.equals(actual) && qualityRank(maxQuality) <= qualityRank(SQ)) {
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
                int bitrate = SQ.equals(actual) ? 0 : (STANDARD.equals(actual) ? STANDARD_KBPS : HQ_KBPS);
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

    /**
     * 解析音频参数：先走原生容器头解析（微秒级），失败时用内置 FFmpeg 兜底。
     *
     * <p>兜底让「扩展名写错、容器不在白名单、JNI 库没加载、文件头被改坏」等情况也能拿到参数，
     * 只有连 FFmpeg 都读不出来时才抛异常。
     */
    public static Probe probe(Path source) throws IOException {
        Path absolute = source.toAbsolutePath();
        if (NativeAudioQuality.isAvailable()) {
            long[] values = NativeAudioQuality.probe(absolute.toString());
            if (values != null && values.length >= 5 && values[0] >= 0) {
                return new Probe(qualityFromNative(values[0]), (int) values[1], (int) values[2], (int) values[3], (int) values[4]);
            }
        }
        Probe fallback = probeWithFfmpeg(absolute);
        if (fallback != null) {
            return fallback;
        }
        throw new IOException("无法解析音频参数: " + source);
    }

    private static final Pattern FFMPEG_DURATION =
            Pattern.compile("Duration:\\s*(\\d+):(\\d{2}):(\\d{2}(?:\\.\\d+)?)");
    private static final Pattern FFMPEG_STREAM =
            Pattern.compile("Stream #\\d+:\\d+[^\\n]*?: Audio: (\\w+)[^\\n]*?, (\\d+) Hz, ([^,\\n]+), ([^,\\n]+)(?:, (\\d+) kb/s)?(?:,|\\s|$)");
    private static final Pattern FFMPEG_CONTAINER_BITRATE =
            Pattern.compile("Duration:[^\\n]*bitrate: (\\d+) kb/s");
    private static final Pattern FFMPEG_BIT_DEPTH = Pattern.compile("(\\d+) bit");
    private static final Pattern FFMPEG_CHANNELS = Pattern.compile("(\\d+) channels");

    /** 用 FFmpeg 读取任意容器/编码的音轨参数；只在原生解析失败时调用，避免常态化起进程。 */
    private static Probe probeWithFfmpeg(Path source) {
        try {
            String ffmpeg = resolveFfmpeg();
            Process process = new ProcessBuilder(ffmpeg, "-hide_banner", "-nostdin", "-i", source.toString())
                    .redirectErrorStream(true)
                    .start();
            String output;
            try (InputStream in = process.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                logger.warn("FFmpeg 兜底解析超时: {}", source);
                return null;
            }
            return parseFfmpegProbe(output, source);
        } catch (Exception e) {
            logger.warn("FFmpeg 兜底解析音质失败: {}", source, e);
            return null;
        }
    }

    private static Probe parseFfmpegProbe(String output, Path source) throws IOException {
        Matcher stream = FFMPEG_STREAM.matcher(output);
        if (!stream.find()) {
            return null;
        }
        String codec = stream.group(1).toLowerCase(Locale.ROOT);
        int sampleRate = Integer.parseInt(stream.group(2));
        int channels = channelCount(stream.group(3));
        int bits = sampleBits(stream.group(4));
        long bitrate = stream.group(5) != null ? Long.parseLong(stream.group(5)) * 1000L : 0L;

        double durationSeconds = 0;
        Matcher duration = FFMPEG_DURATION.matcher(output);
        if (duration.find()) {
            durationSeconds = Integer.parseInt(duration.group(1)) * 3600
                    + Integer.parseInt(duration.group(2)) * 60
                    + Double.parseDouble(duration.group(3));
        }
        if (bitrate <= 0) {
            Matcher container = FFMPEG_CONTAINER_BITRATE.matcher(output);
            if (container.find()) {
                bitrate = Long.parseLong(container.group(1)) * 1000L;
            }
        }
        if (bitrate <= 0 && durationSeconds > 0 && Files.isRegularFile(source)) {
            bitrate = (long) (Files.size(source) * 8 / durationSeconds);
        }
        if (sampleRate <= 0 || bitrate <= 0) {
            return null;
        }

        String quality = isLosslessCodec(codec)
                ? ((sampleRate >= 96000 || bits >= 24) ? HIRES : SQ)
                : qualityFromBitrate((int) bitrate);
        return new Probe(quality, (int) Math.min(bitrate, Integer.MAX_VALUE), sampleRate, bits, channels);
    }

    private static int channelCount(String layout) {
        String value = layout.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("mono")) return 1;
        if (value.startsWith("stereo")) return 2;
        Matcher many = FFMPEG_CHANNELS.matcher(value);
        if (many.find()) return Integer.parseInt(many.group(1));
        return switch (value) {
            case "2.1" -> 3;
            case "4.0", "quad" -> 4;
            case "5.1", "5.1(side)" -> 6;
            case "7.1" -> 8;
            default -> 2;
        };
    }

    private static int sampleBits(String sampleFormat) {
        Matcher explicit = FFMPEG_BIT_DEPTH.matcher(sampleFormat);
        if (explicit.find()) {
            return Integer.parseInt(explicit.group(1));
        }
        String value = sampleFormat.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("u8") || value.startsWith("s8")) return 8;
        if (value.startsWith("s32") || value.startsWith("u32")) return 32;
        if (value.startsWith("s64")) return 64;
        return 16;
    }

    private static boolean isLosslessCodec(String codec) {
        return codec.startsWith("pcm_")
                || switch (codec) {
                    case "flac", "alac", "wavpack", "ape", "tta", "tak", "shorten", "mlp", "truehd", "dst" -> true;
                    default -> codec.startsWith("dsd_");
                };
    }

    private static String qualityFromBitrate(int bps) {
        if (bps >= 441000) return SQ;
        if (bps >= 320000) return HQ;
        if (bps > 0) return STANDARD;
        return HQ;
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
        return MusicAssetLocator.derivedDir(musicId)
                .resolve(quality + (SQ.equals(quality) ? ".flac" : ".mp3"));
    }

    private static String resolveFfmpeg() throws IOException {
        String configured = System.getProperty("neko.ffmpeg");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        if (Main.getConfigManager() == null) {
            return BundledFfmpegSupport.resolve("", false);
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
