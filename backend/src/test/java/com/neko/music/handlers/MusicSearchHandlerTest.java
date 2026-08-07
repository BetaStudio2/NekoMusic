package com.neko.music.handlers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicSearchHandlerTest {

    @Test
    void skipsLyricsWhenMetadataHitIsStrong() {
        assertFalse(MusicSearchHandler.shouldSearchLyricsForQuery("想见你只想见你", 80, true));
    }

    @Test
    void skipsShortCjkQueriesWithMetadataResults() {
        assertFalse(MusicSearchHandler.shouldSearchLyricsForQuery("晴天", 60, true));
        assertFalse(MusicSearchHandler.shouldSearchLyricsForQuery("想你", 0, false));
    }

    @Test
    void allowsLongCjkLyricFragments() {
        assertTrue(MusicSearchHandler.shouldSearchLyricsForQuery("想见你只想见你", 60, true));
    }

    @Test
    void skipsCjkQueriesUpToSixCharacters() {
        assertFalse(MusicSearchHandler.shouldSearchLyricsForQuery("只想见你", 50, true));
        assertFalse(MusicSearchHandler.shouldSearchLyricsForQuery("只想见你", 0, false));
        assertFalse(MusicSearchHandler.shouldSearchLyricsForQuery("我真的想你", 0, false));
    }

    @Test
    void skipsOneOrTwoEnglishWordsAndPinyinLikeQueries() {
        assertFalse(MusicSearchHandler.shouldSearchLyricsForQuery("love", 0, false));
        assertFalse(MusicSearchHandler.shouldSearchLyricsForQuery("qing tian", 0, false));
        assertFalse(MusicSearchHandler.shouldSearchLyricsForQuery("love story", 0, false));
    }

    @Test
    void allowsInformativeEnglishLyricFragments() {
        assertTrue(MusicSearchHandler.shouldSearchLyricsForQuery("never gonna give", 0, false));
        assertTrue(MusicSearchHandler.shouldSearchLyricsForQuery("hello from the other side", 60, true));
    }

    @Test
    void skipsNumericOnlyQueries() {
        assertFalse(MusicSearchHandler.shouldSearchLyricsForQuery("2024", 0, false));
    }

}
