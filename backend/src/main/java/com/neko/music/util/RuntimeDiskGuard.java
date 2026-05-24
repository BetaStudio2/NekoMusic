package com.neko.music.util;

import com.neko.music.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 检测运行目录（{@code user.dir}）所在文件系统的可用空间；
 * 不足阈值时禁止音乐上传与网易云自动补全入库。
 */
public final class RuntimeDiskGuard {
    private static final Logger logger = LoggerFactory.getLogger(RuntimeDiskGuard.class);

    private static final long CHECK_CACHE_MS = 5_000;

    private static volatile CachedCheck cached;

    private RuntimeDiskGuard() {
    }

    public record DiskStatus(boolean sufficient, long usableBytes, long requiredBytes) {
    }

    /** 当前是否允许音乐写入（上传 / 网易云补全入库）。 */
    public static boolean hasSufficientSpaceForMusicWrites() {
        return currentStatus().sufficient();
    }

    public static DiskStatus currentStatus() {
        long now = System.currentTimeMillis();
        CachedCheck hit = cached;
        if (hit != null && now - hit.checkedAtMs < CHECK_CACHE_MS) {
            return hit.status;
        }
        DiskStatus fresh = probe();
        cached = new CachedCheck(now, fresh);
        return fresh;
    }

    public static String uploadBlockedMessage() {
        int gb = Main.getConfigManager().getStorageMinFreeGb();
        return "磁盘可用空间不足，已暂停音乐上传（运行目录所在分区需保留至少 " + gb + "GB 可用空间）";
    }

    public static String neteaseFillBlockedMessage() {
        int gb = Main.getConfigManager().getStorageMinFreeGb();
        return "未找到匹配的音乐（磁盘可用空间不足，已暂停网易云自动补全；需保留至少 " + gb + "GB 可用空间）";
    }

    /**
     * 在音乐上传 / 网易云补全等写入操作前记录运行目录分区空间（INFO）。
     *
     * @param operation 操作说明，如「管理员上传音乐」「网易云自动补全」
     * @param detail    可选补充信息（搜索词等），可为 null
     */
    public static void logStorageForOperation(String operation, String detail) {
        DiskStatus status = currentStatus();
        Path base = MusicAssetLocator.baseDir().toAbsolutePath().normalize();
        String op = detail == null || detail.isBlank()
                ? operation
                : operation + " | " + detail;

        if (status.usableBytes() < 0) {
            logger.info("[存储空间] {} | 运行目录={} | 可用=检测失败 | 写入阈值={} | 允许写入=false",
                    op, base, formatBytes(status.requiredBytes()));
            return;
        }

        long totalBytes = -1L;
        try {
            if (Files.exists(base)) {
                FileStore store = Files.getFileStore(base);
                totalBytes = store.getTotalSpace();
            }
        } catch (IOException e) {
            logger.debug("读取分区总容量失败: {}", e.toString());
        }

        if (totalBytes > 0) {
            long usedBytes = Math.max(0L, totalBytes - status.usableBytes());
            logger.info(
                    "[存储空间] {} | 运行目录={} | 分区总={} | 已用≈{} | 可用={} | 写入阈值={} | 允许写入={}",
                    op, base, formatBytes(totalBytes), formatBytes(usedBytes),
                    formatBytes(status.usableBytes()), formatBytes(status.requiredBytes()), status.sufficient());
        } else {
            logger.info(
                    "[存储空间] {} | 运行目录={} | 可用={} | 写入阈值={} | 允许写入={}",
                    op, base, formatBytes(status.usableBytes()),
                    formatBytes(status.requiredBytes()), status.sufficient());
        }

        if (!status.sufficient()) {
            logger.warn("[存储空间] {} 因可用空间低于阈值已拒绝写入", op);
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 0) {
            return "未知";
        }
        double gb = bytes / (double) Main.getConfigManager().getStorageBytesPerGb();
        if (gb >= 1.0) {
            return String.format(Locale.ROOT, "%.2f GB (%d B)", gb, bytes);
        }
        double mb = bytes / 1_000_000.0;
        if (mb >= 1.0) {
            return String.format(Locale.ROOT, "%.2f MB (%d B)", mb, bytes);
        }
        return bytes + " B";
    }

    private static DiskStatus probe() {
        long required = Main.getConfigManager().getStorageMinFreeBytes();
        Path base = MusicAssetLocator.baseDir();
        try {
            if (!Files.exists(base)) {
                Files.createDirectories(base);
            }
            long usable = Files.getFileStore(base).getUsableSpace();
            boolean ok = usable >= required;
            if (!ok) {
                logger.warn("运行目录磁盘空间不足: base={}, usable={} bytes, required={} bytes",
                        base.toAbsolutePath().normalize(), usable, required);
            }
            return new DiskStatus(ok, usable, required);
        } catch (IOException e) {
            logger.warn("无法检测运行目录磁盘空间，将禁止音乐写入: base={}, err={}",
                    base.toAbsolutePath().normalize(), e.toString());
            return new DiskStatus(false, -1L, required);
        }
    }

    private record CachedCheck(long checkedAtMs, DiskStatus status) {
    }
}
