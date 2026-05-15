package com.neko.music.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class VideoRenderPaths {
    public static final String VIDEO_REL_DIR = "Music/videos";

    private VideoRenderPaths() {
    }

    public static Path videoDir() {
        return MusicAssetLocator.baseDir().resolve(VIDEO_REL_DIR);
    }

    public static Path outputFile(String jobId) {
        return videoDir().resolve(jobId + ".mp4");
    }

    public static String outputRelPath(String jobId) {
        return VIDEO_REL_DIR + "/" + jobId + ".mp4";
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
