package com.neko.music.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 将已通过校验的临时音频落盘到业务目录。
 *
 * <p>先复制到目标目录内的暂存文件（避免跨盘 {@link Files#move} 失败），再原子替换目标路径：
 * 写入过程中断时目标位置的原文件仍然完好，不会出现「旧文件已删、新文件没写完」的中间状态。
 */
public final class TempAudioSpool {

    private TempAudioSpool() {
    }

    public static void commitReplace(Path tempFile, Path destinationFile) throws IOException {
        AtomicFiles.stageAndReplace(tempFile, destinationFile);
        Files.deleteIfExists(tempFile);
    }
}
