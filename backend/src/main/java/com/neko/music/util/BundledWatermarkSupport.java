package com.neko.music.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;

/**
 * 从 JAR 内嵌 {@code watermark.png} 自动释放到 {@code /tmp/.neko/video-render/watermark.png}，
 * 供 FFmpeg overlay 使用；JAR 资源变更时按 SHA-256 重新释放。
 */
public final class BundledWatermarkSupport {
    private static final Logger logger = LoggerFactory.getLogger(BundledWatermarkSupport.class);

    private static final String RESOURCE_PATH = "/watermark.png";
    private static final String FILE_NAME = "watermark.png";

    private static volatile Path cachedFile;
    private static volatile String cachedJarHash;

    private BundledWatermarkSupport() {
    }

    /** 启动或渲染前调用，确保水印 PNG 已释放到磁盘（JAR 变更时会重新释放）。 */
    public static Path ensureWatermarkFile() throws IOException {
        synchronized (BundledWatermarkSupport.class) {
            String jarHash = hashResource(RESOURCE_PATH);
            if (jarHash == null) {
                throw new IOException("JAR 内未找到水印资源: " + RESOURCE_PATH);
            }
            if (cachedFile != null
                    && Files.isRegularFile(cachedFile)
                    && jarHash.equals(cachedJarHash)) {
                return cachedFile;
            }
            cachedFile = releaseBundledWatermark(jarHash);
            cachedJarHash = jarHash;
            return cachedFile;
        }
    }

    /** 供渲染日志输出：路径、大小、尺寸、磁盘/JAR 哈希是否一致。 */
    public static String describeForLog(Path watermarkFile) {
        try {
            long size = Files.size(watermarkFile);
            String diskHash = hashFile(watermarkFile);
            String jarHash = hashResource(RESOURCE_PATH);
            String dimensions = readPngDimensions(watermarkFile);
            boolean hashMatch = jarHash != null && jarHash.equals(diskHash);
            return String.format(
                    "path=%s size=%dB dimensions=%s diskSha256=%s jarSha256=%s hashMatch=%s",
                    watermarkFile.toAbsolutePath(), size, dimensions,
                    shortHash(diskHash), shortHash(jarHash), hashMatch);
        } catch (Exception e) {
            return "path=" + watermarkFile.toAbsolutePath() + " describeError=" + e.getMessage();
        }
    }

    private static Path releaseBundledWatermark(String jarHash) throws IOException {
        Path dir = VideoRenderPaths.videoRenderDir();
        Files.createDirectories(dir);
        Path target = VideoRenderPaths.watermarkFile();
        Path marker = VideoRenderPaths.watermarkMarkerFile();

        String markerHash = readMarkerHash(marker);
        boolean cacheHit = Files.isRegularFile(target) && jarHash.equals(markerHash);

        if (cacheHit) {
            logger.info("视频渲染水印已就绪（缓存命中）: {} {}", target.toAbsolutePath(), describeForLog(target));
            return target;
        }

        logger.info("视频渲染水印准备释放: target={} jarSha256={} markerSha256={} reason={}",
                target.toAbsolutePath(), shortHash(jarHash), shortHash(markerHash),
                markerHash.isEmpty() ? "首次释放" : "JAR 资源已变更");

        try (InputStream in = BundledWatermarkSupport.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                throw new IOException("JAR 内未找到水印资源: " + RESOURCE_PATH);
            }
            Path tmp = Files.createTempFile(dir, "watermark-", ".tmp");
            try {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(tmp);
            }
        }
        Files.writeString(marker, jarHash);
        logger.info("视频渲染水印已释放: {}", describeForLog(target));
        return target;
    }

    private static String readPngDimensions(Path file) {
        try {
            BufferedImage img = ImageIO.read(file.toFile());
            if (img == null) {
                return "unknown";
            }
            return img.getWidth() + "x" + img.getHeight();
        } catch (IOException e) {
            return "read-failed";
        }
    }

    private static String hashFile(Path file) throws IOException {
        MessageDigest md = newDigest();
        try (InputStream in = Files.newInputStream(file)) {
            digestStream(md, in);
        }
        return bytesToHex(md.digest());
    }

    private static String hashResource(String resourcePath) throws IOException {
        try (InputStream in = BundledWatermarkSupport.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            MessageDigest md = newDigest();
            digestStream(md, in);
            return bytesToHex(md.digest());
        }
    }

    private static MessageDigest newDigest() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 不可用", e);
        }
    }

    private static void digestStream(MessageDigest md, InputStream in) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) {
            if (n > 0) {
                md.update(buf, 0, n);
            }
        }
    }

    private static String readMarkerHash(Path marker) {
        if (!Files.isRegularFile(marker)) {
            return "";
        }
        try {
            return Files.readString(marker).trim();
        } catch (IOException e) {
            return "";
        }
    }

    private static String shortHash(String hash) {
        if (hash == null || hash.length() < 12) {
            return hash == null ? "null" : hash;
        }
        return hash.substring(0, 12) + "…";
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
