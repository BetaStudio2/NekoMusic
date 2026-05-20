package com.neko.music.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 音频文件格式验证工具类
 * 使用文件魔数（Magic Number）验证音频文件的真实格式
 */
public class AudioFileValidator {

    /**
     * 支持的音频格式
     */
    public enum AudioFormat {
        MP3,
        FLAC,
        WAV
    }

    /**
     * 验证音频文件格式
     *
     * @param inputStream 文件输入流
     * @param expectedFormat 期望的格式
     * @return 验证结果
     */
    public static ValidationResult validate(InputStream inputStream, AudioFormat expectedFormat) {
        try {
            byte[] header = new byte[12];
            int bytesRead = inputStream.read(header);
            
            if (bytesRead < 4) {
                return ValidationResult.fail("文件太小，无法确定格式");
            }

            switch (expectedFormat) {
                case MP3:
                    return validateMP3(header, inputStream, bytesRead);
                case FLAC:
                    return validateFLAC(header);
                case WAV:
                    return validateWAV(header);
                default:
                    return ValidationResult.fail("不支持的音频格式");
            }
        } catch (IOException e) {
            return ValidationResult.fail("读取文件失败: " + e.getMessage());
        }
    }

    /**
     * 验证 MP3 格式
     * MP3 文件可能有以下几种格式：
     * 1. ID3v2 标签：以 "ID3" 开头
     * 2. 无 ID3 标签：以 MPEG 同步字节开头 (0xFF 0xFB 或 0xFF 0xFA)
     */
    private static ValidationResult validateMP3(byte[] header, InputStream inputStream, int bytesRead) throws IOException {
        // 检查是否有 ID3v2 标签
        if (header.length >= 3 && header[0] == 0x49 && header[1] == 0x44 && header[2] == 0x33) {
            // ID3v2 标签，进一步验证 ID3 版本
            if (header.length >= 5) {
                byte versionMajor = header[3];
                byte versionMinor = header[4];
                // ID3v2.2, ID3v2.3, ID3v2.4 都是有效的
                if (versionMajor >= 2 && versionMajor <= 4) {
                    return ValidationResult.success();
                }
            }
            return ValidationResult.fail("无效的 ID3v2 标签版本");
        }

        // 检查 MPEG 同步字节
        // MP3 帧头以 0xFF 开头，第二字节的高 3 位必须是 111 (即 0xE0)
        // 所以第二字节应该是 0xFA, 0xFB, 0xFC, 0xFD, 0xFE, 0xFF
        if (header[0] == (byte) 0xFF) {
            byte secondByte = header[1];
            // 检查 MPEG 音频的同步模式
            if ((secondByte & 0xE0) == 0xE0) {
                // 进一步验证 MPEG 层版本
                int mpegVersion = (secondByte >> 3) & 0x03;
                int layer = (secondByte >> 1) & 0x03;
                
                // MPEG Version: 00 = MPEG Version 2.5, 01 = reserved, 10 = MPEG Version 2, 11 = MPEG Version 1
                // Layer: 00 = reserved, 01 = Layer III, 10 = Layer II, 11 = Layer I
                if (mpegVersion != 0x01 && layer != 0x00) {
                    return ValidationResult.success();
                }
            }
        }

        // 如果前 12 个字节中没有找到 MP3 标记，尝试读取更多字节
        // 有些 MP3 文件可能有 ID3v1 标签在文件末尾，但我们需要从开头识别
        byte[] moreBytes = new byte[4096];
        int totalRead = bytesRead;
        
        if (totalRead < moreBytes.length) {
            int additionalRead = inputStream.read(moreBytes, bytesRead, moreBytes.length - bytesRead);
            if (additionalRead > 0) {
                totalRead += additionalRead;
            }
        }
        
        // 在读取的字节中搜索 MPEG 同步字节
        for (int i = 0; i < totalRead - 1; i++) {
            if (moreBytes[i] == (byte) 0xFF && i < moreBytes.length - 1) {
                byte secondByte = moreBytes[i + 1];
                if ((secondByte & 0xE0) == 0xE0) {
                    int mpegVersion = (secondByte >> 3) & 0x03;
                    int layer = (secondByte >> 1) & 0x03;
                    if (mpegVersion != 0x01 && layer != 0x00) {
                        return ValidationResult.success();
                    }
                }
            }
        }

        return ValidationResult.fail("不是有效的 MP3 文件");
    }

    /**
     * 验证 FLAC 格式
     * FLAC 文件以 "fLaC" 开头
     */
    private static ValidationResult validateFLAC(byte[] header) {
        if (header.length >= 4 &&
            header[0] == 0x66 && header[1] == 0x4C && 
            header[2] == 0x61 && header[3] == 0x43) {
            return ValidationResult.success();
        }
        return ValidationResult.fail("不是有效的 FLAC 文件");
    }

    /**
     * 验证 WAV 格式
     * WAV 文件以 "RIFF" 开头，后面跟着 "WAVE"
     */
    private static ValidationResult validateWAV(byte[] header) {
        if (header.length >= 12 &&
            header[0] == 0x52 && header[1] == 0x49 && 
            header[2] == 0x46 && header[3] == 0x46 && // "RIFF"
            header[8] == 0x57 && header[9] == 0x41 && 
            header[10] == 0x56 && header[11] == 0x45) { // "WAVE"
            return ValidationResult.success();
        }
        return ValidationResult.fail("不是有效的 WAV 文件");
    }

    /**
     * 检测音频文件的实际格式（不依赖扩展名）
     *
     * @param inputStream 文件输入流
     * @return 检测到的音频格式，如果无法识别则返回 null
     */
    public static AudioFormat detectFormat(InputStream inputStream) {
        try {
            // 标记输入流位置，以便后续重置
            if (inputStream.markSupported()) {
                inputStream.mark(4096);
            }

            byte[] header = new byte[12];
            int bytesRead = inputStream.read(header);
            
            if (bytesRead < 4) {
                if (inputStream.markSupported()) {
                    inputStream.reset();
                }
                return null;
            }

            AudioFormat detectedFormat = null;

            // 检查 FLAC
            if (header.length >= 4 &&
                header[0] == 0x66 && header[1] == 0x4C && 
                header[2] == 0x61 && header[3] == 0x43) {
                detectedFormat = AudioFormat.FLAC;
            }
            // 检查 WAV
            else if (header.length >= 12 &&
                     header[0] == 0x52 && header[1] == 0x49 && 
                     header[2] == 0x46 && header[3] == 0x46 &&
                     header[8] == 0x57 && header[9] == 0x41 && 
                     header[10] == 0x56 && header[11] == 0x45) {
                detectedFormat = AudioFormat.WAV;
            }
            // 检查 MP3
            else if (header.length >= 3 && 
                     (header[0] == 0x49 && header[1] == 0x44 && header[2] == 0x33)) {
                detectedFormat = AudioFormat.MP3;
            }
            // 检查 MP3 (无 ID3 标签)
            else if (header[0] == (byte) 0xFF) {
                byte secondByte = header[1];
                if ((secondByte & 0xE0) == 0xE0) {
                    int mpegVersion = (secondByte >> 3) & 0x03;
                    int layer = (secondByte >> 1) & 0x03;
                    if (mpegVersion != 0x01 && layer != 0x00) {
                        detectedFormat = AudioFormat.MP3;
                    }
                }
            }
            // 如果前面的检测都失败了，尝试在更多字节中搜索 MP3 帧
            else if (detectedFormat == null) {
                detectedFormat = detectMP3InStream(inputStream, bytesRead, header);
            }

            if (inputStream.markSupported()) {
                inputStream.reset();
            }

            return detectedFormat;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 在流中搜索 MP3 帧
     */
    private static AudioFormat detectMP3InStream(InputStream inputStream, int bytesRead, byte[] initialHeader) throws IOException {
        byte[] searchBuffer = new byte[4096];
        System.arraycopy(initialHeader, 0, searchBuffer, 0, bytesRead);
        
        if (bytesRead < searchBuffer.length) {
            int additionalRead = inputStream.read(searchBuffer, bytesRead, searchBuffer.length - bytesRead);
            if (additionalRead > 0) {
                bytesRead += additionalRead;
            }
        }
        
        // 在缓冲区中搜索 MP3 同步字节
        for (int i = 0; i < bytesRead - 1; i++) {
            if (searchBuffer[i] == (byte) 0xFF) {
                byte secondByte = searchBuffer[i + 1];
                if ((secondByte & 0xE0) == 0xE0) {
                    int mpegVersion = (secondByte >> 3) & 0x03;
                    int layer = (secondByte >> 1) & 0x03;
                    if (mpegVersion != 0x01 && layer != 0x00) {
                        return AudioFormat.MP3;
                    }
                }
            }
        }
        
        return null;
    }

    /**
     * 检测文件的实际格式并验证是否为支持的格式
     * 返回详细的结果，包括检测到的格式和验证信息
     *
     * @param inputStream 文件输入流
     * @param fileExtension 文件扩展名（用于验证）
     * @return 格式检测结果
     */
    public static FormatDetectionResult detectAndValidate(InputStream inputStream, String fileExtension) {
        try {
            if (inputStream.markSupported()) {
                inputStream.mark(4096);
            }

            byte[] header = new byte[12];
            int bytesRead = inputStream.read(header);
            
            if (bytesRead < 4) {
                if (inputStream.markSupported()) {
                    inputStream.reset();
                }
                return FormatDetectionResult.fail("文件太小，无法确定格式");
            }

            // 检测实际格式
            AudioFormat detectedFormat = null;
            String formatDescription = null;

            // 检查 FLAC
            if (header.length >= 4 &&
                header[0] == 0x66 && header[1] == 0x4C && 
                header[2] == 0x61 && header[3] == 0x43) {
                detectedFormat = AudioFormat.FLAC;
                formatDescription = "FLAC";
            }
            // 检查 WAV
            else if (header.length >= 12 &&
                     header[0] == 0x52 && header[1] == 0x49 && 
                     header[2] == 0x46 && header[3] == 0x46 &&
                     header[8] == 0x57 && header[9] == 0x41 && 
                     header[10] == 0x56 && header[11] == 0x45) {
                detectedFormat = AudioFormat.WAV;
                formatDescription = "WAV";
            }
            // 检查 MP3 (ID3v2)
            else if (header.length >= 3 && 
                     (header[0] == 0x49 && header[1] == 0x44 && header[2] == 0x33)) {
                detectedFormat = AudioFormat.MP3;
                formatDescription = "MP3 (ID3v2)";
            }
            // 检查 MP3 (无 ID3)
            else if (header[0] == (byte) 0xFF) {
                byte secondByte = header[1];
                if ((secondByte & 0xE0) == 0xE0) {
                    int mpegVersion = (secondByte >> 3) & 0x03;
                    int layer = (secondByte >> 1) & 0x03;
                    if (mpegVersion != 0x01 && layer != 0x00) {
                        detectedFormat = AudioFormat.MP3;
                        formatDescription = "MP3 (原始)";
                    }
                }
            }

            // 如果前面的检测都失败了，尝试在更多字节中搜索 MP3 帧
            if (detectedFormat == null) {
                detectedFormat = detectMP3InStream(inputStream, bytesRead, header);
                if (detectedFormat != null) {
                    formatDescription = "MP3 (深度扫描)";
                }
            }

            if (inputStream.markSupported()) {
                inputStream.reset();
            }

            // 检查不支持的格式
            if (detectedFormat == null) {
                // 检查常见的视频格式
                if (bytesRead >= 4) {
                    // MP4/M4A (ftyp)
                    if (header[4] == 0x66 && header[5] == 0x74 && 
                        header[6] == 0x79 && header[7] == 0x70) {
                        return FormatDetectionResult.fail("检测到 MP4/M4A 格式，这不是音频文件");
                    }
                    // OGG
                    if (header[0] == 0x4F && header[1] == 0x67 && 
                        header[2] == 0x67 && header[3] == 0x53) {
                        return FormatDetectionResult.fail("检测到 OGG 格式，目前不支持");
                    }
                    // AVI
                    if (header[0] == 0x52 && header[1] == 0x49 && 
                        header[2] == 0x46 && header[3] == 0x46 &&
                        header[8] == 0x41 && header[9] == 0x56 && 
                        header[10] == 0x49) {
                        return FormatDetectionResult.fail("检测到 AVI 视频格式，这不是音频文件");
                    }
                }
                return FormatDetectionResult.fail("无法识别文件格式，不是有效的音频文件");
            }

            // 验证文件扩展名是否与实际格式匹配
            if (fileExtension != null && !fileExtension.isEmpty()) {
                // 去掉扩展名开头的点号（如果有），统一为不带点号的格式
                String ext = fileExtension.toLowerCase();
                if (ext.startsWith(".")) {
                    ext = ext.substring(1);
                }
                
                boolean extensionMatches = false;
                
                switch (detectedFormat) {
                    case MP3:
                        extensionMatches = ext.equals("mp3");
                        break;
                    case FLAC:
                        extensionMatches = ext.equals("flac");
                        break;
                    case WAV:
                        extensionMatches = ext.equals("wav");
                        break;
                }
                
                if (!extensionMatches) {
                    return FormatDetectionResult.fail(
                            String.format("文件扩展名 '%s' 与实际格式 '%s' 不匹配", 
                                    fileExtension, formatDescription));
                }
            }

            return FormatDetectionResult.success(detectedFormat, formatDescription);
        } catch (IOException e) {
            return FormatDetectionResult.fail("读取文件失败: " + e.getMessage());
        }
    }

    /**
     * 基于磁盘路径做魔数 + 扩展名检测（先读至多 512KB 前缀，避免大文件全读入内存）。
     */
    public static FormatDetectionResult detectAndValidatePath(Path path, String fileExtension) {
        try {
            long size = Files.size(path);
            if (size < 4) {
                return FormatDetectionResult.fail("文件太小，无法确定格式");
            }
            int peek = (int) Math.min(size, 512 * 1024);
            byte[] prefix = new byte[peek];
            int totalRead = 0;
            try (InputStream in = Files.newInputStream(path)) {
                while (totalRead < peek) {
                    int n = in.read(prefix, totalRead, peek - totalRead);
                    if (n <= 0) {
                        break;
                    }
                    totalRead += n;
                }
            }
            try (ByteArrayInputStream bais = new ByteArrayInputStream(prefix, 0, totalRead)) {
                return detectAndValidate(bais, fileExtension);
            }
        } catch (IOException e) {
            return FormatDetectionResult.fail("读取文件失败: " + e.getMessage());
        }
    }

    /**
     * 格式检测结果类
     */
    public static class FormatDetectionResult {
        private final boolean valid;
        private final AudioFormat format;
        private final String formatDescription;
        private final String errorMessage;

        private FormatDetectionResult(boolean valid, AudioFormat format, String formatDescription, String errorMessage) {
            this.valid = valid;
            this.format = format;
            this.formatDescription = formatDescription;
            this.errorMessage = errorMessage;
        }

        public static FormatDetectionResult success(AudioFormat format, String formatDescription) {
            return new FormatDetectionResult(true, format, formatDescription, null);
        }

        public static FormatDetectionResult fail(String errorMessage) {
            return new FormatDetectionResult(false, null, null, errorMessage);
        }

        public boolean isValid() {
            return valid;
        }

        public AudioFormat getFormat() {
            return format;
        }

        public String getFormatDescription() {
            return formatDescription;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * 验证结果类
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult fail(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}