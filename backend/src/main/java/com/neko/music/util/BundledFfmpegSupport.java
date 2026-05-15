package com.neko.music.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 从 JAR 内嵌的静态 FFmpeg 解压到运行目录，或在系统 PATH / 常见路径中查找。
 */
public final class BundledFfmpegSupport {
    private static final Logger logger = LoggerFactory.getLogger(BundledFfmpegSupport.class);

    private static volatile String cachedPath;

    private BundledFfmpegSupport() {
    }

    /**
     * @param configuredPath config.yml 中的 ffmpeg_path；{@code auto}、{@code ffmpeg}、空 表示自动
     * @param preferBundled  为 true 时优先使用 JAR 内嵌二进制（避免 systemd 等环境 PATH 过短）
     */
    public static String resolve(String configuredPath, boolean preferBundled) throws IOException {
        if (cachedPath != null) {
            Path cached = Paths.get(cachedPath);
            if (Files.isRegularFile(cached) && Files.isExecutable(cached)) {
                return cachedPath;
            }
            cachedPath = null;
        }
        synchronized (BundledFfmpegSupport.class) {
            if (cachedPath != null) {
                return cachedPath;
            }
            String resolved = resolveInternal(configuredPath, preferBundled);
            cachedPath = resolved;
            logger.info("视频渲染 FFmpeg 可执行文件: {}", resolved);
            return resolved;
        }
    }

    private static String resolveInternal(String configuredPath, boolean preferBundled) throws IOException {
        if (isExplicitPath(configuredPath)) {
            Path explicit = Paths.get(configuredPath.trim());
            if (Files.isRegularFile(explicit) && Files.isExecutable(explicit)) {
                return explicit.toAbsolutePath().normalize().toString();
            }
            throw new IOException("配置的 ffmpeg_path 不可执行: " + configuredPath);
        }

        if (preferBundled) {
            String bundled = tryExtractBundled();
            if (bundled != null) {
                return bundled;
            }
        }

        for (String candidate : commonCandidates()) {
            Path p = Paths.get(candidate);
            if (Files.isRegularFile(p) && Files.isExecutable(p)) {
                return p.toAbsolutePath().normalize().toString();
            }
        }

        String onPath = findOnPath("ffmpeg");
        if (onPath != null) {
            return onPath;
        }

        if (!preferBundled) {
            String bundled = tryExtractBundled();
            if (bundled != null) {
                return bundled;
            }
        }

        String platform = detectPlatformKey();
        throw new IOException("未找到 FFmpeg。"
                + (platform == null
                ? " 当前系统不受内嵌包支持，请安装 ffmpeg 或在 config.yml 设置 video_render.ffmpeg_path"
                : " 请使用 mvn package 重新打包（会内嵌 " + platform + " 静态 FFmpeg），或配置 ffmpeg_path"));
    }

    private static boolean isExplicitPath(String configuredPath) {
        if (configuredPath == null) {
            return false;
        }
        String t = configuredPath.trim();
        if (t.isEmpty()) {
            return false;
        }
        String lower = t.toLowerCase(Locale.ROOT);
        return !"auto".equals(lower) && !"ffmpeg".equals(lower);
    }

    private static String[] commonCandidates() {
        return new String[]{
                "/usr/bin/ffmpeg",
                "/usr/local/bin/ffmpeg",
                "/bin/ffmpeg"
        };
    }

    private static String findOnPath(String name) {
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", "command -v " + name);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out;
            try (InputStream in = p.getInputStream()) {
                out = new String(in.readAllBytes()).trim();
            }
            if (!p.waitFor(5, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            if (p.exitValue() != 0 || out.isBlank()) {
                return null;
            }
            Path path = Paths.get(out.split("\\s+")[0]);
            if (Files.isRegularFile(path) && Files.isExecutable(path)) {
                return path.toAbsolutePath().normalize().toString();
            }
        } catch (Exception e) {
            logger.debug("PATH 查找 ffmpeg 失败: {}", e.getMessage());
        }
        return null;
    }

    /** @return linux-x86_64 / linux-aarch64，不支持则 null */
    static String detectPlatformKey() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("linux")) {
            return null;
        }
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (arch.equals("amd64") || arch.equals("x86_64")) {
            return "linux-x86_64";
        }
        if (arch.equals("aarch64") || arch.equals("arm64")) {
            return "linux-aarch64";
        }
        return null;
    }

    private static String tryExtractBundled() throws IOException {
        String platform = detectPlatformKey();
        if (platform == null) {
            return null;
        }
        String resourcePath = "/native/" + platform + "/ffmpeg";
        InputStream in = BundledFfmpegSupport.class.getResourceAsStream(resourcePath);
        if (in == null) {
            logger.warn("JAR 内未找到内嵌 FFmpeg 资源: {}", resourcePath);
            return null;
        }

        Path cacheDir = MusicAssetLocator.baseDir().resolve(".neko").resolve("ffmpeg").resolve(platform);
        Files.createDirectories(cacheDir);
        Path target = cacheDir.resolve("ffmpeg");
        Path versionMarker = cacheDir.resolve(".bundled.ok");

        if (Files.isRegularFile(target) && Files.isExecutable(target) && Files.isRegularFile(versionMarker)) {
            in.close();
            return target.toAbsolutePath().normalize().toString();
        }

        Path tmp = Files.createTempFile(cacheDir, "ffmpeg-", ".tmp");
        try (in) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        makeExecutable(tmp);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        Files.writeString(versionMarker, "bundled-" + platform);
        return target.toAbsolutePath().normalize().toString();
    }

    private static void makeExecutable(Path file) throws IOException {
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_READ,
                    PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(file, perms);
        } catch (UnsupportedOperationException e) {
            file.toFile().setExecutable(true);
        }
    }
}
