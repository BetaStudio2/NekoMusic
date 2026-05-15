package com.neko.music.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class VideoRenderPaths {
    /** 渲染成片目录（系统临时目录，重启后可能被清理） */
    public static final Path VIDEO_DIR = Paths.get("/tmp/NekoMusic-video-render");

    private VideoRenderPaths() {
    }

    public static Path videoDir() {
        return VIDEO_DIR;
    }

    public static Path outputFile(String jobId) {
        return videoDir().resolve(jobId + ".mp4");
    }

    public static String outputRelPath(String jobId) {
        return outputFile(jobId).toAbsolutePath().normalize().toString();
    }

    public static void ensureVideoDir() throws IOException {
        Files.createDirectories(videoDir());
    }

    public static boolean isAllowedOutput(Path file) {
        return MusicAssetLocator.isUnderDirectory(file, videoDir());
    }

    public static String truncateText(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        if (t.length() <= maxLen) {
            return t;
        }
        return t.substring(0, maxLen - 1) + "…";
    }

    public static Path assFile(String jobId) throws IOException {
        Path dir = MusicAssetLocator.baseDir().resolve(".neko/video-render");
        Files.createDirectories(dir);
        return dir.resolve(jobId + ".ass");
    }

    /** ASS 事件行内文本转义 */
    public static String escapeAssText(String text) {
        if (text == null || text.isEmpty()) {
            return " ";
        }
        return text
                .replace("\\", "\\\\")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("\r", "")
                .replace("\n", "\\N");
    }

    /** subtitles 滤镜中的文件路径转义（Linux） */
    public static String escapeSubtitlesPath(Path assFile) {
        return escapeFilterPath(assFile);
    }

    public static String escapeFilterPath(Path path) {
        return path.toAbsolutePath().normalize().toString()
                .replace("\\", "\\\\")
                .replace(":", "\\:")
                .replace("'", "\\'");
    }

    /** subtitles=ass:fontsdir=... 参数字符串 */
    public static String subtitlesFilterArg(Path assFile, Path fontsDir) {
        return "subtitles='" + escapeFilterPath(assFile) + "':fontsdir='" + escapeFilterPath(fontsDir) + "'";
    }
}
