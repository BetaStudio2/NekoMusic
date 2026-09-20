package com.neko.music.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 落地文件时先写同目录暂存文件、再原子替换目标，保证替换失败时目标原文件不被破坏。
 *
 * <p>原先多处（音频、封面）直接复制到目标路径，一旦写入中途失败，旧文件已经被截断/删除，
 * 只能留下一个损坏的目标文件。这里统一改成「暂存 + 原子改名」。
 */
public final class AtomicFiles {

    private AtomicFiles() {
    }

    /** 以 {@code source} 的内容原子替换 {@code destination}。 */
    public static void stageAndReplace(Path source, Path destination) throws IOException {
        Path parent = parentOf(destination);
        Path staging = Files.createTempFile(parent, ".neko-staging-", ".part");
        try {
            Files.copy(source, staging, StandardCopyOption.REPLACE_EXISTING);
            moveReplacing(staging, destination);
        } finally {
            Files.deleteIfExists(staging);
        }
    }

    /**
     * 读取 {@code source} 并原子替换 {@code destination}；读取结束后关闭流。
     */
    public static void writeAndReplace(InputStream source, Path destination) throws IOException {
        Path parent = parentOf(destination);
        Path staging = Files.createTempFile(parent, ".neko-staging-", ".part");
        try {
            try (InputStream in = source) {
                Files.copy(in, staging, StandardCopyOption.REPLACE_EXISTING);
            }
            moveReplacing(staging, destination);
        } finally {
            Files.deleteIfExists(staging);
        }
    }

    private static Path parentOf(Path destination) throws IOException {
        Path parent = destination.toAbsolutePath().getParent();
        if (parent == null) {
            parent = Paths.get("").toAbsolutePath();
        }
        Files.createDirectories(parent);
        return parent;
    }

    private static void moveReplacing(Path staging, Path destination) throws IOException {
        try {
            Files.move(staging, destination,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(staging, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
