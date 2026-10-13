package com.neko.music.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * MP3 最简完整性校验：拦截「错误页 / 纯文本」被误当音频入库，同时不误伤正常 MP3。
 */
class AudioIntegrityValidatorTest {

    /** MPEG1 Layer III、无 CRC 的帧头（0xFF 0xFB 0x90 ...）。 */
    private static final byte[] FRAME = {(byte) 0xFF, (byte) 0xFB, (byte) 0x90, (byte) 0x64};

    @TempDir
    Path tempDir;

    @Test
    void acceptsMp3StartingWithFrameHeader() throws IOException {
        Path mp3 = tempDir.resolve("plain.mp3");
        Files.write(mp3, FRAME);
        assertNull(AudioIntegrityValidator.validateSavedFile(mp3, AudioFileValidator.AudioFormat.MP3));
    }

    @Test
    void acceptsMp3WithId3v2TagBeforeFrame() throws IOException {
        Path mp3 = tempDir.resolve("tagged.mp3");
        try (OutputStream out = Files.newOutputStream(mp3)) {
            out.write(new byte[] {'I', 'D', '3', 3, 0, 0, 0, 0, 0x02, 0x00});
            out.write(new byte[256]);
            out.write(FRAME);
        }
        assertNull(AudioIntegrityValidator.validateSavedFile(mp3, AudioFileValidator.AudioFormat.MP3));
    }

    @Test
    void rejectsHtmlErrorPageSavedAsMp3() throws IOException {
        Path fake = tempDir.resolve("error.mp3");
        Files.writeString(fake, "<html><body>404 Not Found</body></html>");
        assertNotNull(AudioIntegrityValidator.validateSavedFile(fake, AudioFileValidator.AudioFormat.MP3));
    }

    @Test
    void rejectsMissingFile() {
        assertNotNull(AudioIntegrityValidator.validateSavedFile(
                tempDir.resolve("nope.mp3"), AudioFileValidator.AudioFormat.MP3));
    }
}
