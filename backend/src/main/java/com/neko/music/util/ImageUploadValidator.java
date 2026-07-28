package com.neko.music.util;

import jakarta.servlet.http.Part;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

/**
 * Validates uploaded images by file extension, declared MIME type, and actual file signature.
 */
public final class ImageUploadValidator {
    public static final long DEFAULT_MAX_IMAGE_BYTES = 10L * 1024 * 1024;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp"
    );
    private static final int MAX_IMAGE_DIMENSION = 20_000;
    private static final long MAX_IMAGE_PIXELS = 100_000_000L;

    private ImageUploadValidator() {
    }

    public static ValidationResult validatePart(Part part, long maxBytes) {
        if (part == null || part.getSize() <= 0) {
            return ValidationResult.fail("未上传图片文件");
        }
        if (maxBytes > 0 && part.getSize() > maxBytes) {
            return ValidationResult.fail("图片大小超过" + (maxBytes / 1024 / 1024) + "MiB限制");
        }

        String extension = extensionOf(part.getSubmittedFileName());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return ValidationResult.fail("不支持的图片格式，只支持: jpg, jpeg, png, gif, webp, bmp");
        }

        String contentType = part.getContentType();
        if (contentType != null && !contentType.isBlank()
                && !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return ValidationResult.fail("只支持图片文件");
        }

        DetectedImageFormat detected;
        try {
            detected = detectFormat(part);
        } catch (IOException e) {
            return ValidationResult.fail("读取图片文件失败: " + e.getMessage());
        }
        if (detected == null) {
            return ValidationResult.fail("图片文件格式错误或文件已损坏");
        }
        if (!extensionMatchesFormat(extension, detected)) {
            return ValidationResult.fail("图片扩展名与实际格式不匹配");
        }

        return ValidationResult.success(extension, detected);
    }

    public static String extensionOf(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        String normalized = fileName.replace('\\', '/');
        String baseName = normalized.substring(normalized.lastIndexOf('/') + 1);
        int lastDotIndex = baseName.lastIndexOf('.');
        if (lastDotIndex <= 0 || lastDotIndex == baseName.length() - 1) {
            return "";
        }
        return baseName.substring(lastDotIndex).toLowerCase(Locale.ROOT);
    }

    private static DetectedImageFormat detectFormat(Part part) throws IOException {
        byte[] header;
        try (InputStream in = part.getInputStream()) {
            header = in.readNBytes(32);
        }

        DetectedImageFormat magic = detectByMagic(header, header.length, part.getSize());
        if (magic == DetectedImageFormat.WEBP) {
            return magic;
        }

        try (InputStream in = part.getInputStream();
             ImageInputStream imageInput = ImageIO.createImageInputStream(in)) {
            if (imageInput == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                String formatName = reader.getFormatName();
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (!dimensionsAllowed(width, height)) {
                    return null;
                }
                return normalizeFormat(formatName);
            } finally {
                reader.dispose();
            }
        }
    }

    private static DetectedImageFormat detectByMagic(byte[] h, int len, long fileSize) {
        if (len >= 12
                && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P'
                && isValidWebpHeader(h, len, fileSize)) {
            return DetectedImageFormat.WEBP;
        }
        return null;
    }

    private static boolean isValidWebpHeader(byte[] h, int len, long fileSize) {
        if (len < 16) {
            return false;
        }
        long riffPayloadSize = uint32Le(h, 4);
        if (fileSize < 20 || riffPayloadSize < 12 || riffPayloadSize > fileSize - 8) {
            return false;
        }

        boolean vp8 = h[12] == 'V' && h[13] == 'P' && h[14] == '8' && h[15] == ' ';
        boolean vp8l = h[12] == 'V' && h[13] == 'P' && h[14] == '8' && h[15] == 'L';
        boolean vp8x = h[12] == 'V' && h[13] == 'P' && h[14] == '8' && h[15] == 'X';
        if (!vp8 && !vp8l && !vp8x) {
            return false;
        }

        long chunkSize = len >= 20 ? uint32Le(h, 16) : -1;
        if (chunkSize <= 0 || chunkSize > fileSize - 20) {
            return false;
        }

        if (vp8) {
            return len >= 30
                    && h[23] == (byte) 0x9d
                    && h[24] == 0x01
                    && h[25] == 0x2a
                    && dimensionsAllowed(uint16Le(h, 26), uint16Le(h, 28));
        }
        if (vp8l) {
            return len >= 25
                    && chunkSize >= 5
                    && h[20] == 0x2f
                    && dimensionsAllowed(webpLosslessWidth(h), webpLosslessHeight(h));
        }
        return len >= 30
                && chunkSize == 10
                && dimensionsAllowed(uint24Le(h, 24) + 1, uint24Le(h, 27) + 1);
    }

    private static boolean dimensionsAllowed(int width, int height) {
        return width > 0
                && height > 0
                && width <= MAX_IMAGE_DIMENSION
                && height <= MAX_IMAGE_DIMENSION
                && (long) width * height <= MAX_IMAGE_PIXELS;
    }

    private static long uint32Le(byte[] h, int offset) {
        return ((long) h[offset] & 0xff)
                | (((long) h[offset + 1] & 0xff) << 8)
                | (((long) h[offset + 2] & 0xff) << 16)
                | (((long) h[offset + 3] & 0xff) << 24);
    }

    private static int uint16Le(byte[] h, int offset) {
        return (h[offset] & 0xff) | ((h[offset + 1] & 0xff) << 8);
    }

    private static int uint24Le(byte[] h, int offset) {
        return (h[offset] & 0xff)
                | ((h[offset + 1] & 0xff) << 8)
                | ((h[offset + 2] & 0xff) << 16);
    }

    private static int webpLosslessWidth(byte[] h) {
        return 1 + ((h[21] & 0xff) | ((h[22] & 0x3f) << 8));
    }

    private static int webpLosslessHeight(byte[] h) {
        return 1 + (((h[22] & 0xc0) >> 6) | ((h[23] & 0xff) << 2) | ((h[24] & 0x0f) << 10));
    }

    private static DetectedImageFormat normalizeFormat(String formatName) {
        if (formatName == null) {
            return null;
        }
        String f = formatName.trim().toLowerCase(Locale.ROOT);
        return switch (f) {
            case "jpeg", "jpg" -> DetectedImageFormat.JPEG;
            case "png" -> DetectedImageFormat.PNG;
            case "gif" -> DetectedImageFormat.GIF;
            case "bmp", "bitmap" -> DetectedImageFormat.BMP;
            default -> null;
        };
    }

    private static boolean extensionMatchesFormat(String extension, DetectedImageFormat format) {
        return switch (format) {
            case JPEG -> ".jpg".equals(extension) || ".jpeg".equals(extension);
            case PNG -> ".png".equals(extension);
            case GIF -> ".gif".equals(extension);
            case WEBP -> ".webp".equals(extension);
            case BMP -> ".bmp".equals(extension);
        };
    }

    private enum DetectedImageFormat {
        JPEG,
        PNG,
        GIF,
        WEBP,
        BMP
    }

    public static final class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        private final String extension;
        private final DetectedImageFormat detectedFormat;

        private ValidationResult(boolean valid, String errorMessage, String extension,
                                 DetectedImageFormat detectedFormat) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.extension = extension;
            this.detectedFormat = detectedFormat;
        }

        private static ValidationResult success(String extension, DetectedImageFormat detectedFormat) {
            return new ValidationResult(true, null, extension, detectedFormat);
        }

        public static ValidationResult fail(String errorMessage) {
            return new ValidationResult(false, errorMessage, "", null);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public String getExtension() {
            return extension;
        }

        public String getExtensionWithoutDot() {
            return extension.startsWith(".") ? extension.substring(1) : extension;
        }

        public String getDetectedFormatName() {
            return detectedFormat == null ? "" : detectedFormat.name().toLowerCase(Locale.ROOT);
        }
    }
}
