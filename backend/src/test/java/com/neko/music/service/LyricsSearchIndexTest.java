package com.neko.music.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 歌词检索：索引侧与查询侧必须用同一套归一（否则繁体歌词永远搜不到简体词），
 * 并按 2-gram 覆盖率容忍按记忆搜索时漏掉的一两个字。
 */
class LyricsSearchIndexTest {

    private static final String TRADITIONAL_LRC =
            "[00:12.00]來自中原的一群夥伴\n[00:16.00]啦啦啦啦啦啦\n";

    @Test
    void matchesSimplifiedQueryAgainstTraditionalLyrics() throws Exception {
        LyricsSearchIndex index = index(148, TRADITIONAL_LRC, null);

        assertEquals(List.of(148), index.searchWithStats("来自中原的一群伙伴", 81).ids());
    }

    @Test
    void normalizesLegacyUnnormalizedPlainText() throws Exception {
        // 历史行里的 plain_text 可能还是未归一的繁體原文，索引时必须再归一
        LyricsSearchIndex index = index(148, TRADITIONAL_LRC, "來自中原的一群夥伴 啦啦啦啦啦啦");

        assertEquals(List.of(148), index.searchWithStats("来自中原的一群伙伴", 81).ids());
    }

    @Test
    void toleratesOneMissingCharacterInLongQuery() throws Exception {
        LyricsSearchIndex index = index(148, TRADITIONAL_LRC, null);

        assertEquals(List.of(148), index.searchWithStats("来自中原一群伙伴", 81).ids());
    }

    @Test
    void toleratesOneInsertedCharacter() throws Exception {
        // 用户按记忆多打了一个「的」：插入一个字会打断两个相邻 gram（原的 / 的一）
        LyricsSearchIndex index = index(148, "[00:12.00]來自中原一群夥伴\n[00:16.00]啦啦啦啦啦\n", null);

        assertEquals(List.of(148), index.searchWithStats("来自中原的一群伙伴", 81).ids());
        assertEquals(List.of(148), index.searchWithStats("来自中原一群伙伴", 81).ids());
    }

    @Test
    void fallsBackToNearMissOnlyWhenNothingMatchesExactly() throws Exception {
        LyricsSearchIndex index = new LyricsSearchIndex();
        assertTrue(indexOne(index, 149, "[00:10.00]来自中原的一群小伙伴\n[00:14.00]啦啦啦啦啦\n", null));
        markReady(index);

        assertEquals(List.of(149), index.searchWithStats("来自中原的一群伙伴", 81).ids());
    }

    @Test
    void prefersExactPhraseOverNearMiss() throws Exception {
        LyricsSearchIndex index = new LyricsSearchIndex();
        assertTrue(indexOne(index, 148, TRADITIONAL_LRC, null));
        assertTrue(indexOne(index, 149, "[00:10.00]来自中原的一群小伙伴\n[00:14.00]啦啦啦啦啦\n", null));
        markReady(index);

        assertEquals(List.of(148), index.searchWithStats("来自中原的一群伙伴", 81).ids());
    }

    @Test
    void rejectsUnrelatedFragments() throws Exception {
        LyricsSearchIndex index = index(148, TRADITIONAL_LRC, null);

        assertEquals(List.of(), index.searchWithStats("晴天娃娃的故事", 81).ids());
    }

    @Test
    void ignoresQueriesShorterThanMinimum() throws Exception {
        LyricsSearchIndex index = index(148, TRADITIONAL_LRC, null);

        assertEquals(List.of(), index.searchWithStats("来自中原", 81).ids());
    }

    private static LyricsSearchIndex index(int musicId, String lrc, String precomputedPlain) throws Exception {
        LyricsSearchIndex index = new LyricsSearchIndex();
        assertTrue(indexOne(index, musicId, lrc, precomputedPlain));
        markReady(index);
        return index;
    }

    private static boolean indexOne(LyricsSearchIndex index, int musicId, String lrc, String precomputedPlain)
            throws Exception {
        Method indexOne = LyricsSearchIndex.class.getDeclaredMethod(
                "indexOne", int.class, String.class, String.class);
        indexOne.setAccessible(true);
        return (boolean) indexOne.invoke(index, musicId, lrc, precomputedPlain);
    }

    private static void markReady(LyricsSearchIndex index) throws Exception {
        Field ready = LyricsSearchIndex.class.getDeclaredField("ready");
        ready.setAccessible(true);
        ((AtomicBoolean) ready.get(index)).set(true);
    }
}
