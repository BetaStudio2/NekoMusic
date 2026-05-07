package com.neko.music.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 在运行目录下按 {@code Music/music/{id}.*}、{@code Music/covers/{id}.*} 定位音频与封面，不依赖数据库路径列。
 */
public final class MusicAssetLocator {
    private static final Logger log = LoggerFactory.getLogger(MusicAssetLocator.class);

    public static final String AUDIO_REL_DIR = "Music/music";
    public static final String COVER_REL_DIR = "Music/covers";

    private static final List<String> AUDIO_EXT_PRIORITY = List.of(
            "flac", "wav", "mp3", "m4a", "aac", "ogg");
    private static final List<String> COVER_EXT_PRIORITY = List.of(
            "png", "jpg", "jpeg", "webp", "gif", "bmp");

    private MusicAssetLocator() {
    }

    public static Path baseDir() {
        return Paths.get(System.getProperty("user.dir"));
    }

    public static Path audioDir() {
        return baseDir().resolve(AUDIO_REL_DIR);
    }

    public static Path coverDir() {
        return baseDir().resolve(COVER_REL_DIR);
    }

    public static Optional<Path> findAudioFile(int musicId) {
        if (musicId <= 0) {
            return Optional.empty();
        }
        try {
            return pickOne(listIdNamedFiles(audioDir(), musicId), AUDIO_EXT_PRIORITY);
        } catch (IOException e) {
            log.warn("列出音乐目录失败: {}", e.toString());
            return Optional.empty();
        }
    }

    public static Optional<Path> findCoverFile(int musicId) {
        if (musicId <= 0) {
            return Optional.empty();
        }
        try {
            return pickOne(listIdNamedFiles(coverDir(), musicId), COVER_EXT_PRIORITY);
        } catch (IOException e) {
            log.warn("列出封面目录失败: {}", e.toString());
            return Optional.empty();
        }
    }

    public static String fileApiUrl(int musicId) {
        return "/api/music/file/" + musicId;
    }

    public static String coverApiUrl(int musicId) {
        return "/api/music/cover/" + musicId;
    }

    public static void deleteAudioVariants(int musicId) throws IOException {
        deleteIdNamedFiles(audioDir(), musicId);
    }

    public static void deleteCoverVariants(int musicId) throws IOException {
        deleteIdNamedFiles(coverDir(), musicId);
    }

    /** 防止路径逃逸：仅当 {@code file} 规范化后位于 {@code expectedDir} 下时返回 true。 */
    public static boolean isUnderDirectory(Path file, Path expectedDir) {
        Path base = expectedDir.toAbsolutePath().normalize();
        Path p = file.toAbsolutePath().normalize();
        return p.startsWith(base);
    }

    /**
     * 形如 {@code 12.mp3}（id 后仅一段扩展名），排除 {@code 12.a.mp3} 等。
     */
    static boolean isSingleSegmentExtension(String fileName, String idPrefix) {
        if (!fileName.startsWith(idPrefix)) {
            return false;
        }
        String rest = fileName.substring(idPrefix.length());
        return !rest.isEmpty() && !rest.contains(".");
    }

    private static List<Path> listIdNamedFiles(Path dir, int musicId) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        String prefix = musicId + ".";
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> isSingleSegmentExtension(p.getFileName().toString(), prefix))
                    .collect(Collectors.toList());
        }
    }

    private static Optional<Path> pickOne(List<Path> candidates, List<String> extPriority) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Path> byExt = new HashMap<>();
        for (Path p : candidates) {
            String name = p.getFileName().toString();
            int dot = name.lastIndexOf('.');
            if (dot <= 0) {
                continue;
            }
            String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
            byExt.putIfAbsent(ext, p);
        }
        for (String pref : extPriority) {
            if (byExt.containsKey(pref)) {
                return Optional.of(byExt.get(pref));
            }
        }
        return candidates.stream().min(Comparator.comparing(p -> p.getFileName().toString()));
    }

    private static void deleteIdNamedFiles(Path dir, int musicId) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        String prefix = musicId + ".";
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> toDelete = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> isSingleSegmentExtension(p.getFileName().toString(), prefix))
                    .collect(Collectors.toList());
            for (Path p : toDelete) {
                Files.deleteIfExists(p);
            }
        }
    }
}
