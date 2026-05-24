package com.neko.music.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LrcValidatorTest {

    @Test
    void acceptsStandardLrc() {
        String lrc = """
                [ti:Stay Me]
                [00:12.50]いつもの時間に間に合わなくて
                [00:18.00]イライラしちゃっても手は抜かない
                """;
        assertTrue(validate(lrc).isValid());
    }

    @Test
    void rejectsPlainTextLyrics() {
        String lrc = """
                作词 : 藤原　マリア
                作曲 : 藤原　マリア
                Everyday, everytime, everywhere, stay true to myself.
                いつもの時間に間に合わなくて
                """;
        assertFalse(validate(lrc).isValid());
    }

    @Test
    void rejectsMetadataOnlyWithoutTimestamps() {
        String lrc = """
                [ti:Stay Me]
                [ar:Artist]
                """;
        assertFalse(validate(lrc).isValid());
    }

    private static LrcValidator.ValidationResult validate(String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return LrcValidator.validate(new ByteArrayInputStream(bytes), bytes.length);
    }
}
