package com.neko.music.service;

import com.neko.music.util.PinyinUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 搜索排序规则：先匹配歌名、再匹配歌手，并保证「歌名命中」恒优于「仅歌手命中」。
 */
class MusicSearchRankerTest {

    @Test
    void splitsArtistAndTitleInEitherOrder() {
        assertEquals(List.of("周杰伦", "晴天"), MusicSearchRanker.splitTerms("周杰伦，晴天"));
        assertEquals(List.of("晴天", "周杰伦"), MusicSearchRanker.splitTerms("晴天 周杰伦"));
        assertEquals(List.of("周杰伦", "晴天"), MusicSearchRanker.splitTerms("周杰伦-晴天"));
        assertEquals(List.of("晴天", "周杰伦"), MusicSearchRanker.splitTerms("晴天/周杰伦"));
        assertEquals(List.of("晴天", "周杰伦"), MusicSearchRanker.splitTerms("  晴天 ｜ 周杰伦  "));
    }

    @Test
    void keepsSingleKeywordQueriesIntact() {
        assertEquals(List.of("晴天"), MusicSearchRanker.splitTerms("晴天"));
        assertEquals(List.of("晴天"), MusicSearchRanker.splitTerms("  晴天  "));
        assertEquals(List.of("晴天"), MusicSearchRanker.splitTerms("晴天，晴天"));
        assertEquals(List.of(), MusicSearchRanker.splitTerms("   "));
        assertEquals(List.of(), MusicSearchRanker.splitTerms(null));
    }

    @Test
    void keepsLongEnglishPhraseAsSingleTerm() {
        assertEquals(List.of("hello from the other side"),
                MusicSearchRanker.splitTerms("hello from the other side"));
    }

    @Test
    void capsTermCount() {
        List<String> terms = MusicSearchRanker.splitTerms("晴天 雨天 阴天 雪天 风天 雾天 云天");
        assertEquals(6, terms.size());
        assertEquals("晴天", terms.get(0));
    }

    @Test
    void titleMatchAlwaysOutranksArtistOnlyMatch() {
        int titleHit = MusicSearchRanker.score(fields("晴天", "周杰伦", "叶惠美"), List.of("晴天"));
        int artistHit = MusicSearchRanker.score(fields("稻香", "晴天", "魔杰座"), List.of("晴天"));

        assertTrue(artistHit > 0, "歌手命中应有分");
        assertTrue(titleHit > artistHit * 100, "歌名命中必须压倒性地排在仅歌手命中之前");
    }

    @Test
    void combinedQueryIsOrderIndependentAndBeatsTitleOnlyMatch() {
        List<String> comma = MusicSearchRanker.splitTerms("周杰伦，晴天");
        List<String> slash = MusicSearchRanker.splitTerms("晴天/周杰伦");
        List<String> dash = MusicSearchRanker.splitTerms("周杰伦-晴天");

        int both = MusicSearchRanker.score(fields("晴天", "周杰伦", "叶惠美"), comma);
        assertEquals(both, MusicSearchRanker.score(fields("晴天", "周杰伦", "叶惠美"), slash));
        assertEquals(both, MusicSearchRanker.score(fields("晴天", "周杰伦", "叶惠美"), dash));

        int sameTitleOtherArtist = MusicSearchRanker.score(fields("晴天", "张杰", "叶惠美"), comma);
        assertTrue(sameTitleOtherArtist > 0);
        assertTrue(both > sameTitleOtherArtist, "歌名与歌手同时命中应排在只有歌名命中之前");
    }

    @Test
    void exactBeatsPrefixBeatsContains() {
        List<String> term = List.of("晴天");
        int exact = MusicSearchRanker.score(fields("晴天", "周杰伦", "叶惠美"), term);
        int prefix = MusicSearchRanker.score(fields("晴天娃娃", "周杰伦", "叶惠美"), term);
        int contains = MusicSearchRanker.score(fields("我的晴天", "周杰伦", "叶惠美"), term);

        assertTrue(exact > prefix, "精确匹配应排在前缀匹配之前");
        assertTrue(prefix > contains, "前缀匹配应排在子串匹配之前");
    }

    @Test
    void pinyinQueryStillFindsChineseTitles() {
        int hit = MusicSearchRanker.score(fields("晴天", "周杰伦", "叶惠美"), List.of("qingtian"));
        assertTrue(hit > 0, "拼音词条应能命中中文歌名");
    }

    @Test
    void wholeQueryStrengthOnlyCountsUnsplittedQuery() {
        assertTrue(MusicSearchRanker.wholeQueryStrength(fields("晴天", "周杰伦", "叶惠美"), "晴天") >= 80);
        assertEquals(0, MusicSearchRanker.wholeQueryStrength(fields("晴天", "周杰伦", "叶惠美"), "晴天 周杰伦"));
    }

    private static MusicSearchRanker.MusicFields fields(String title, String artist, String album) {
        return new MusicSearchRanker.MusicFields(
                title, artist, album,
                PinyinUtil.getPinyin(title), PinyinUtil.getPinyinInitials(title), PinyinUtil.getPinyinInitials(title),
                PinyinUtil.getPinyin(artist), PinyinUtil.getPinyinInitials(artist), PinyinUtil.getPinyinInitials(artist),
                PinyinUtil.getPinyin(album));
    }
}
