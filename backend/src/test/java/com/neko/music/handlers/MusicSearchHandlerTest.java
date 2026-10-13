package com.neko.music.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void treatsPlainDigitsAsMusicId() {
        assertEquals(Optional.of(148), MusicSearchHandler.parseMusicId("148"));
        // 前后空白容忍：从地址栏 / 客户端里复制粘贴常带空格
        assertEquals(Optional.of(7), MusicSearchHandler.parseMusicId("  7 "));

        // 带任何分隔符或字母的都不是 ID，交回模糊搜索
        assertEquals(Optional.empty(), MusicSearchHandler.parseMusicId("148 晴天"));
        assertEquals(Optional.empty(), MusicSearchHandler.parseMusicId("晴天"));
        assertEquals(Optional.empty(), MusicSearchHandler.parseMusicId("12a"));
        assertEquals(Optional.empty(), MusicSearchHandler.parseMusicId("2024-01-01"));
        // 全角数字、前导零、0 都不认：否则「007」「１９８９」这类正常搜索会被 ID 抢走
        assertEquals(Optional.empty(), MusicSearchHandler.parseMusicId("１９８９"));
        assertEquals(Optional.empty(), MusicSearchHandler.parseMusicId("007"));
        assertEquals(Optional.empty(), MusicSearchHandler.parseMusicId("0"));
        // 超出 int 范围
        assertEquals(Optional.empty(), MusicSearchHandler.parseMusicId("1234567890"));
        assertEquals(Optional.empty(), MusicSearchHandler.parseMusicId(""));
        assertEquals(Optional.empty(), MusicSearchHandler.parseMusicId(null));
    }

    @Test
    void looksUpMusicByIdWithSameShapeAsFuzzySearch() throws Exception {
        Map<String, Object> columns = idRow();
        AtomicReference<String> sql = new AtomicReference<>();
        AtomicInteger boundId = new AtomicInteger();

        Method findMusicById = MusicSearchHandler.class.getDeclaredMethod(
                "findMusicById", Connection.class, int.class);
        findMusicById.setAccessible(true);
        Object music = findMusicById.invoke(null, stubConnection(columns, true, sql, boundId), 148);

        assertEquals("SELECT id, title, artist, album, duration, upload_user_id, created_at, max_quality"
                + " FROM music WHERE id = ?", sql.get());
        assertEquals(148, boundId.get());
        String json = new ObjectMapper().writeValueAsString(music);
        assertEquals(148, new ObjectMapper().readTree(json).path("id").asInt());
        assertEquals("晴天", new ObjectMapper().readTree(json).path("title").asText());
        // 返回结构必须与模糊搜索同形：客户端直接读 maxQuality 决定音质档位
        assertEquals("sq", new ObjectMapper().readTree(json).path("maxQuality").asText());
        // lrc 的语义是「本次由歌词正文命中」：按 ID 命中与歌词无关，必须是 false
        assertFalse(new ObjectMapper().readTree(json).path("lrc").asBoolean(),
                "ID 命中不是歌词命中，lrc 不能为 true");
    }

    @Test
    void missingIdFallsBackToFuzzySearch() throws Exception {
        Method findMusicById = MusicSearchHandler.class.getDeclaredMethod(
                "findMusicById", Connection.class, int.class);
        findMusicById.setAccessible(true);
        Object music = findMusicById.invoke(null,
                stubConnection(idRow(), false, new AtomicReference<>(), new AtomicInteger()), 999999);

        assertNull(music, "ID 不存在时必须返回 null，让调用方继续走模糊搜索");
    }

    private static Map<String, Object> idRow() {
        Map<String, Object> columns = new HashMap<>();
        columns.put("id", 148);
        columns.put("title", "晴天");
        columns.put("artist", "周杰伦");
        columns.put("album", "叶惠美");
        columns.put("duration", 269);
        columns.put("upload_user_id", 0);
        columns.put("created_at", Timestamp.valueOf("2024-01-01 12:00:00"));
        columns.put("max_quality", "sq");
        return columns;
    }

    /** 伪造 Connection：只实现 prepareStatement，并把 SQL 与绑定的 ID 记下来供断言。 */
    private static Connection stubConnection(Map<String, Object> columns, boolean hasRow,
                                            AtomicReference<String> sql, AtomicInteger boundId) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "prepareStatement" -> {
                        sql.set((String) args[0]);
                        yield stubPreparedStatement(columns, hasRow, boundId);
                    }
                    default -> null;
                });
    }

    private static PreparedStatement stubPreparedStatement(Map<String, Object> columns, boolean hasRow,
                                                           AtomicInteger boundId) {
        AtomicBoolean first = new AtomicBoolean(true);
        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "setInt" -> {
                        boundId.set((Integer) args[1]);
                        yield null;
                    }
                    case "executeQuery" -> resultSet(columns, hasRow, first);
                    default -> null;
                });
    }

    private static ResultSet resultSet(Map<String, Object> columns, boolean hasRow, AtomicBoolean first) {
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "next" -> hasRow && first.getAndSet(false);
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
