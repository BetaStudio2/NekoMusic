package com.neko.music.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void searchResultCarriesMaxQuality() throws Exception {
        // 搜索结果必须带 max_quality：客户端直接用它决定可选音质档位，不再逐条查详情。
        Map<String, Object> columns = new HashMap<>();
        columns.put("id", 1);
        columns.put("title", "晴天");
        columns.put("artist", "周杰伦");
        columns.put("album", "叶惠美");
        columns.put("duration", 269);
        columns.put("upload_user_id", 0);
        columns.put("created_at", Timestamp.valueOf("2024-01-01 12:00:00"));
        columns.put("max_quality", "sq");

        Method mapMusicRow = MusicSearchHandler.class.getDeclaredMethod("mapMusicRow", ResultSet.class);
        mapMusicRow.setAccessible(true);
        Object music = mapMusicRow.invoke(null, stubResultSet(columns));

        String json = new ObjectMapper().writeValueAsString(music);
        assertEquals("sq", new ObjectMapper().readTree(json).path("maxQuality").asText(), json);
    }

    /** 用动态代理伪造单行 ResultSet：只实现 mapMusicRow 用到的取值方法。 */
    private static ResultSet stubResultSet(Map<String, Object> columns) {
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getString" -> columns.get((String) args[0]);
                    case "getInt" -> {
                        Object value = columns.get((String) args[0]);
                        yield value == null ? 0 : ((Number) value).intValue();
                    }
                    case "getTimestamp" -> columns.get((String) args[0]);
                    case "wasNull" -> false;
                    default -> null;
                });
    }

}
