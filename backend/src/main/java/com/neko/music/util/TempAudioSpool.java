package com.neko.music.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 将已通过校验的临时音频落盘到业务目录（复制后删除临时文件，避免跨盘 {@link Files#move} 失败）。
 */
public final class TempAudioSpool {

    private TempAudioSpool() {
    }

    public static void commitReplace(Path tempFile, Path destinationFile) throws IOException {
        Path parent = destinationFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.copy(tempFile, destinationFile, StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(tempFile);
    }
}
