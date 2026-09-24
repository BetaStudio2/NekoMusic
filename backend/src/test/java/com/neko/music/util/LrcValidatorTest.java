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

    @Test
    void acceptsBilingualTranslationLines() {
        String lrc = """
                [00:12.50]Original line
                {"Translation line"}
                [00:18.00]Another line
                {'Another translation'}
                """;
        assertTrue(validate(lrc).isValid());
    }

    @Test
    void rejectsTranslationWithoutQuotes() {
        String lrc = """
                [00:12.50]Original line
                {Translation line}
                """;
        assertFalse(validate(lrc).isValid());
    }

    @Test
    void normalizesNeteaseColonTimestamps() {
        String netease = "[00:00:26]First\n[00:12:1]Second\n[00:03]Missing milliseconds\n[00:20.48]Already standard";
        String normalized = LrcValidator.normalizeNeteaseTimestamps(netease);

        assertTrue(normalized.contains("[00:00.26]First"));
        assertTrue(normalized.contains("[00:12.1]Second"));
        assertTrue(normalized.contains("[00:03.0]Missing milliseconds"));
        assertTrue(normalized.contains("[00:20.48]Already standard"));
        assertTrue(validate(normalized).isValid());
    }

    @Test
    void expandsNeteaseMultipleTimestamps() {
        String netease = "[01:17.17][00:39.72]我在捷运线的车子里\n"
                + "[03:01.05][02:42.90][01:36.12][00:58.92]I'll be leaving you";
        String normalized = LrcValidator.normalizeNeteaseTimestamps(netease);

        assertTrue(normalized.contains("[01:17.17]我在捷运线的车子里"));
        assertTrue(normalized.contains("[00:39.72]我在捷运线的车子里"));
        assertTrue(validate(normalized).isValid());
    }

    @Test
    void acceptsSixtySecondTimestamp() {
        assertTrue(validate("[00:60.00]歌词").isValid());
    }

    private static LrcValidator.ValidationResult validate(String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return LrcValidator.validate(new ByteArrayInputStream(bytes), bytes.length);
    }
}
