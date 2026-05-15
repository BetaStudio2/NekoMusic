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

    public static String escapeDrawText(String text) {
        if (text == null || text.isEmpty()) {
            return " ";
        }
        return text
                .replace("\\", "\\\\")
                .replace(":", "\\:")
                .replace("'", "\\'")
                .replace("%", "\\%")
                .replace("\n", " ")
                .replace("\r", " ");
    }

    public static String truncateDrawText(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        if (t.length() <= maxLen) {
            return t;
        }
        return t.substring(0, maxLen - 1) + "…";
    }
}
