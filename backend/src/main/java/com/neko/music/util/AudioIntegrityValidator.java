package com.neko.music.util;

import com.neko.music.Main;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.TagException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * 上传接口用：拦截损坏比特流及明显非无损的 WAV（如 ADPCM）、缺少 FLAC 魔数的文件。
 * MP3 不校验。不判断「有损洗 FLAC」。
 */
public final class AudioIntegrityValidator {

    private static final Logger LOG = LoggerFactory.getLogger(AudioIntegrityValidator.class);

    private static final byte[] SUBTYPE_PCM_LE = {
            0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x10, 0x00,
            (byte) 0x80, 0x00, 0x00, (byte) 0xaa, 0x38, (byte) 0x9b, 0x71
    };
    private static final byte[] SUBTYPE_IEEE_FLOAT_LE = {
            0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x10, 0x00,
            (byte) 0x80, 0x00, 0x00, (byte) 0xaa, 0x38, (byte) 0x9b, 0x71
    };

    private AudioIntegrityValidator() {
    }

    /**
     * MP3 最简完整性校验：跳过 ID3v2 标签后，在前 8KiB 内找到合法的 MPEG 帧同步头。
     *
     * <p>只挡「错误页 / 纯文本 / 截断」这类明显坏文件（例如上游返回的 HTML 被误当音频落盘），
     * 不做解码，也不判断码率与音质。</p>
     */
    private static boolean hasMpegFrameHeader(Path path) {
        byte[] head;
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            long offset = readId3v2Size(raf);
            long remaining = raf.length() - offset;
            if (remaining < 4) {
                return false;
            }
            head = new byte[(int) Math.min(8192L, remaining)];
            raf.seek(offset);
            raf.readFully(head);
        } catch (IOException e) {
            return false;
        }
        for (int i = 0; i + 3 < head.length; i++) {
            if (isValidMpegFrameHeader(head, i)) {
                return true;
            }
        }
        return false;
    }

    /** 存在 ID3v2 头时返回「标签头 + 标签体」的总长度，否则返回 0。 */
    private static long readId3v2Size(RandomAccessFile raf) throws IOException {
        if (raf.length() < 10) {
            return 0;
        }
        byte[] header = new byte[10];
        raf.seek(0);
        raf.readFully(header);
        if (header[0] != 'I' || header[1] != 'D' || header[2] != '3') {
            return 0;
        }
        // ID3v2 大小是 4 个 7 位字节（syncsafe），不含 10 字节头。
        long size = ((long) (header[6] & 0x7F) << 21)
                | ((long) (header[7] & 0x7F) << 14)
                | ((long) (header[8] & 0x7F) << 7)
                | (long) (header[9] & 0x7F);
        return 10 + size;
    }

    /** 校验 MPEG 音频帧头：同步字完整、版本/层/码率/采样率均非保留值。 */
    private static boolean isValidMpegFrameHeader(byte[] data, int offset) {
        if ((data[offset] & 0xFF) != 0xFF) {
            return false;
        }
        int b1 = data[offset + 1] & 0xFF;
        if ((b1 & 0xE0) != 0xE0) {
            return false;
        }
        int version = (b1 >> 3) & 0x03;
        int layer = (b1 >> 1) & 0x03;
        if (version == 0x01 || layer == 0x00) {
            return false;
        }
        int b2 = data[offset + 2] & 0xFF;
        int bitrateIndex = (b2 >> 4) & 0x0F;
        int sampleRateIndex = (b2 >> 2) & 0x03;
        return bitrateIndex != 0x0F && sampleRateIndex != 0x03;
    }

    /** @return null 通过；否则为错误文案 */
    public static String validateSavedFile(Path path, AudioFileValidator.AudioFormat format) {
        if (format == AudioFileValidator.AudioFormat.MP3) {
            if (!Files.isRegularFile(path)) {
                return "音频文件不存在";
            }
            return hasMpegFrameHeader(path) ? null : "文件内容不是有效 MP3（缺少 MPEG 帧同步头）";
        }
        if (!Files.isRegularFile(path)) {
            return "音频文件不存在";
        }
        long size;
        try {
            size = Files.size(path);
        } catch (IOException e) {
            return "无法读取文件: " + e.getMessage();
        }
        if (size < 64) {
            return "文件过小，可能损坏或非音频";
        }
        try {
            if (format == AudioFileValidator.AudioFormat.FLAC) {
                if (!hasFlacMagic(path)) {
                    return "文件内容不是有效 FLAC（缺少 fLaC 头）";
                }
            } else if (format == AudioFileValidator.AudioFormat.WAV) {
                String wavErr = validateWavFmtIsPcmOrFloat(path);
                if (wavErr != null) {
                    return wavErr;
                }
            }

            AudioFile af = AudioFileIO.read(path.toFile());
            if (!af.getAudioHeader().isLossless()) {
                return "解析为非无损编码（请使用标准 FLAC 或 PCM/IEEE float WAV）";
            }

            if (format == AudioFileValidator.AudioFormat.FLAC) {
                if (flacCliAvailable()) {
                    if (!runFlacTest(path)) {
                        return "FLAC 已损坏或无法通过解码校验";
                    }
                } else {
                    String ffmpeg = resolveFfmpegOrNull();
                    if (ffmpeg == null) {
                        return "服务器未安装 flac 且无法解析 FFmpeg，无法校验 FLAC（请安装 flac 或配置 video_render.ffmpeg_path）";
                    }
                    if (!ffmpegFullDecode(ffmpeg, path)) {
                        return "FLAC 已损坏或无法解码";
                    }
                }
            } else {
                String ffmpeg = resolveFfmpegOrNull();
                if (ffmpeg != null) {
                    if (!ffmpegFullDecode(ffmpeg, path)) {
                        return "WAV 已损坏或无法完整解码";
                    }
                } else {
                    LOG.debug("未找到 FFmpeg，WAV 仅校验 fmt 与元数据: {}", path);
                }
            }
            return null;
        } catch (CannotReadException e) {
            return "无法解析音频（文件损坏或格式异常）";
        } catch (IOException | TagException | ReadOnlyFileException | InvalidAudioFrameException e) {
            LOG.warn("音频校验异常 path={}: {}", path, e.toString());
            return "校验音频时出错: " + e.getMessage();
        }
    }

    private static boolean hasFlacMagic(Path path) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            if (raf.length() < 4) {
                return false;
            }
            byte[] h = new byte[4];
            raf.readFully(h);
            return h[0] == 0x66 && h[1] == 0x4C && h[2] == 0x61 && h[3] == 0x43;
        }
    }

    static String validateWavFmtIsPcmOrFloat(Path path) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            if (raf.length() < 12) {
                return "WAV 文件过短";
            }
            byte[] riff = new byte[12];
            raf.readFully(riff);
            if (!(riff[0] == 'R' && riff[1] == 'I' && riff[2] == 'F' && riff[3] == 'F'
                    && riff[8] == 'W' && riff[9] == 'A' && riff[10] == 'V' && riff[11] == 'E')) {
                return "不是有效的 RIFF/WAVE 文件";
            }
            long fileLen = raf.length();
            long pos = 12;
            while (pos + 8 <= fileLen) {
                raf.seek(pos);
                byte[] chunkHdr = new byte[8];
                raf.readFully(chunkHdr);
                String chunkId = new String(chunkHdr, 0, 4, StandardCharsets.US_ASCII);
                int chunkSize = ByteBuffer.wrap(chunkHdr, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                if (chunkSize < 0) {
                    return "WAV 块大小异常";
                }
                long dataStart = pos + 8;
                if ("fmt ".equals(chunkId) || "fmt".equals(chunkId)) {
                    if (chunkSize < 16) {
                        return "WAV fmt 块过短";
                    }
                    int toRead = Math.min(chunkSize, 64);
                    byte[] fmt = new byte[toRead];
                    raf.seek(dataStart);
                    raf.readFully(fmt);
                    int wFormatTag = ByteBuffer.wrap(fmt, 0, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                    if (wFormatTag == 1 || wFormatTag == 3) {
                        return null;
                    }
                    if (wFormatTag == 0xFFFE) {
                        if (chunkSize < 40 || fmt.length < 40) {
                            return "WAV 为扩展格式但 fmt 数据不完整";
                        }
                        byte[] sub = Arrays.copyOfRange(fmt, 24, 40);
                        if (Arrays.equals(sub, SUBTYPE_PCM_LE) || Arrays.equals(sub, SUBTYPE_IEEE_FLOAT_LE)) {
                            return null;
                        }
                        return "WAV 子格式不是 PCM / IEEE float";
                    }
                    return "WAV 编码不是无损 PCM / IEEE float（wFormatTag=0x" + Integer.toHexString(wFormatTag) + "）";
                }
                long next = dataStart + (long) chunkSize + (chunkSize % 2);
                if (next <= pos) {
                    return "WAV 块结构损坏";
                }
                pos = next;
            }
            return "WAV 中未找到 fmt 块";
        }
    }

    private static String resolveFfmpegOrNull() {
        try {
            return BundledFfmpegSupport.resolve(
                    Main.getConfigManager().getVideoRenderFfmpegPath(),
                    Main.getConfigManager().isVideoRenderPreferBundledFfmpeg());
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean flacCliAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("flac", "-version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            if (!p.waitFor(5, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    private static boolean runFlacTest(Path path) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("flac", "-s", "-t", path.toAbsolutePath().normalize().toString());
        return runProcessForExit(pb, 5, TimeUnit.MINUTES);
    }

    private static boolean ffmpegFullDecode(String ffmpeg, Path path) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                ffmpeg, "-v", "error", "-nostats", "-i", path.toString(), "-f", "null", "-");
        return runProcessForExit(pb, 5, TimeUnit.MINUTES);
    }

    /** 启动进程并等待退出：超时或非零退出返回 false，中断时恢复中断标志。 */
    private static boolean runProcessForExit(ProcessBuilder pb, long timeout, TimeUnit unit) throws IOException {
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try {
            if (!p.waitFor(timeout, unit)) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
            return false;
        }
    }
}
