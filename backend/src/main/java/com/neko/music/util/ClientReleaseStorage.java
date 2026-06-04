package com.neko.music.util;

import com.neko.music.service.AppReleaseService;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** 客户端安装包目录（直链 {@code GET /update/{文件名}}，固定 {@code {user.dir}/releases}） */
public final class ClientReleaseStorage {

    private static final String RELEASES_DIR_NAME = "releases";

    /** 50 MiB（二进制），非 50 MB（十进制） */
    public static final long MAX_UPLOAD_BYTES = 50L * 1024 * 1024;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".apk", ".exe", ".deb", ".pkg"
    );

    private ClientReleaseStorage() {
    }

    public static Path storageDir() {
        return Path.of(System.getProperty("user.dir")).resolve(RELEASES_DIR_NAME).toAbsolutePath().normalize();
    }

    public static void ensureStorageDir() throws IOException {
        Files.createDirectories(storageDir());
    }

    public static String androidApkFileName(String androidVer) {
        return androidVer + ".apk";
    }

    public static String windowsExeFileName(String pcVer) {
        return "Neko云音乐 Setup " + pcVer + ".exe";
    }

    public static String linuxDebFileName(String pcVer) {
        return "NekoMusic_" + pcVer + "_amd64.deb";
    }

    public static String macPkgFileName(String pcVer) {
        return "Neko云音乐" + pcVer + ".pkg";
    }

    public static String expectedFileNameForPlatform(String platform, AppReleaseService.AppRelease release) {
        if (release == null || platform == null) {
            return null;
        }
        return switch (platform.trim().toLowerCase(Locale.ROOT)) {
            case "android" -> androidApkFileName(release.androidVer());
            case "windows" -> windowsExeFileName(release.pcVer());
            case "linux" -> linuxDebFileName(release.pcVer());
            case "mac" -> macPkgFileName(release.pcVer());
            default -> null;
        };
    }

    public static String requiredExtensionForPlatform(String platform) {
        if (platform == null) {
            return null;
        }
        return switch (platform.trim().toLowerCase(Locale.ROOT)) {
            case "android" -> ".apk";
            case "windows" -> ".exe";
            case "linux" -> ".deb";
            case "mac" -> ".pkg";
            default -> null;
        };
    }

    /** 仅校验上传源文件的类型与体积，不要求文件名与发布名一致 */
    public static void validateSourceFileForPlatform(String platform, String sourceFileName, long sizeBytes) {
        String requiredExt = requiredExtensionForPlatform(platform);
        if (requiredExt == null) {
            throw new IllegalArgumentException("无效的平台: " + platform);
        }
        if (sourceFileName == null || sourceFileName.isBlank()) {
            throw new IllegalArgumentException("无法识别文件名");
        }
        String baseName = Path.of(sourceFileName).getFileName().toString();
        if (!baseName.toLowerCase(Locale.ROOT).endsWith(requiredExt)) {
            throw new IllegalArgumentException("该平台仅允许上传 " + requiredExt.substring(1) + " 文件");
        }
        if (sizeBytes < 0 || sizeBytes > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("安装包体积不得超过 50MiB");
        }
    }

    public static String publicDownloadUrl(String siteBase, String fileName) {
        String base = trimTrailingSlash(siteBase);
        return base + "/update/" + encodePathSegment(fileName);
    }

    public static Optional<Path> resolveReadableFile(String fileName) {
        if (!isSafeFileName(fileName)) {
            return Optional.empty();
        }
        if (!hasAllowedExtension(fileName)) {
            return Optional.empty();
        }
        Path base = storageDir().toAbsolutePath().normalize();
        Path resolved = base.resolve(fileName).normalize();
        if (!resolved.startsWith(base)) {
            return Optional.empty();
        }
        if (!Files.isRegularFile(resolved)) {
            return Optional.empty();
        }
        return Optional.of(resolved);
    }

    public static void validateFileName(String fileName) {
        if (!isSafeFileName(fileName)) {
            throw new IllegalArgumentException("非法文件名");
        }
        if (!hasAllowedExtension(fileName)) {
            throw new IllegalArgumentException("仅允许上传 apk、exe、deb、pkg 安装包");
        }
    }

    public static void validateUpload(String fileName, long sizeBytes) {
        validateFileName(fileName);
        if (sizeBytes < 0 || sizeBytes > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("安装包体积不得超过 50MiB");
        }
    }

    public static Path resolveTargetForUpload(String fileName) throws IOException {
        validateFileName(fileName);
        ensureStorageDir();
        Path base = storageDir().toAbsolutePath().normalize();
        Path resolved = base.resolve(fileName).normalize();
        if (!resolved.startsWith(base)) {
            throw new IllegalArgumentException("非法文件名");
        }
        return resolved;
    }

    public static String contentTypeForFileName(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".apk")) {
            return "application/vnd.android.package-archive";
        }
        if (lower.endsWith(".exe")) {
            return "application/vnd.microsoft.portable-executable";
        }
        if (lower.endsWith(".deb")) {
            return "application/vnd.debian.binary-package";
        }
        if (lower.endsWith(".pkg")) {
            return "application/octet-stream";
        }
        return "application/octet-stream";
    }

    private static boolean isSafeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return false;
        }
        return !fileName.startsWith(".");
    }

    private static boolean hasAllowedExtension(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String ext : ALLOWED_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private static String encodePathSegment(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String trimTrailingSlash(String base) {
        if (base == null || base.isEmpty()) {
            return "";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
