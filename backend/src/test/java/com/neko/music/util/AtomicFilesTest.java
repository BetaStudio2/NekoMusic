package com.neko.music.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicFilesTest {

    @TempDir
    Path tempDir;

    @Test
    void linkOrCopyAndReplaceKeepsSourceAndReplacesDestination() throws Exception {
        Path source = tempDir.resolve("source.mp3");
        Path destination = tempDir.resolve("music").resolve("42.mp3");
        byte[] content = new byte[]{1, 2, 3, 4, 5};
        Files.write(source, content);
        Files.createDirectories(destination.getParent());
        Files.write(destination, new byte[]{9, 9});

        AtomicFiles.linkOrCopyAndReplace(source, destination);

        assertTrue(Files.isRegularFile(source));
        assertArrayEquals(content, Files.readAllBytes(source));
        assertArrayEquals(content, Files.readAllBytes(destination));
    }

    @Test
    void linkOrCopyAndReplaceLeavesNoStagingFile() throws Exception {
        Path source = tempDir.resolve("source.mp3");
        Path destination = tempDir.resolve("music").resolve("42.mp3");
        Files.write(source, new byte[]{1, 2, 3});

        AtomicFiles.linkOrCopyAndReplace(source, destination);

        try (Stream<Path> files = Files.list(destination.getParent())) {
            List<String> names = files.map(path -> path.getFileName().toString()).toList();
            assertEquals(List.of("42.mp3"), names);
        }
    }
}
