package com.neko.music.handlers;

import com.neko.music.Main;
import com.neko.music.util.HttpResourceCache;
import com.neko.music.util.JsonResponseCache;
import com.neko.music.util.MusicAssetLocator;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import com.neko.music.i18n.ApiMessages;

public class LatestMusicHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(LatestMusicHandler.class);

    /** 固定返回条数（客户端不可调）。 */
    private static final int LATEST_LIMIT = 300;

    /**
     * 成品响应体缓存键与 TTL。
     *
     * <p>响应本来就声明 {@code public, max-age=1800}（客户端 / CDN 可持有半小时），所以进程内复用
     * 同一份成品不会比客户端已经接受的口径更旧；TTL 取 10 秒只是为了让「刚上传的歌」尽快出现，
     * 同时把每次请求一次的全表排序查询 + 序列化彻底移出关键路径。</p>
     */
    private static final String CACHE_KEY = "music.latest";

    /** 进程内成品缓存时长（毫秒）。 */
    private static final long CACHE_TTL_MILLIS = 10_000L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 返回条数固定，不接受任何请求参数：本接口响应声明为可被共享缓存（public），
        // 若内容随查询串变化、而边缘缓存的缓存键又不含查询串，同一份缓存条目就会被
        // 其它查询串的请求写坏（Web 缓存投毒）。公开缓存接口只允许存在唯一变体。
        String body = JsonResponseCache.get(CACHE_KEY, CACHE_TTL_MILLIS, this::buildResponseBody);

        response.setStatus(HttpStatus.OK_200);
        response.setContentType("application/json;charset=utf-8");
        response.setHeader("Cache-Control",
                "public, max-age=" + HttpResourceCache.MAX_AGE_HALF_HOUR);
        response.getWriter().write(body);
    }

    /** 查询 + 序列化整份响应（只在缓存未命中时执行）。 */
    private String buildResponseBody() {
        List<LatestMusicItem> latestMusic = getLatestMusic();
        // message 固定用源语言：本接口是 public 共享缓存，文案随 Accept-Language 变化会让缓存
        // 按语言碎片化（要么加 Vary，要么保持唯一变体）。客户端本来也不依赖这句文案。
        LatestMusicResponse latestMusicResponse =
                new LatestMusicResponse(true, ApiMessages.source("music.latest_ok"), latestMusic);
        try {
            return Main.getObjectMapper().writeValueAsString(latestMusicResponse);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // 序列化失败不该被缓存成一份坏响应：抛出去让本次请求走统一的 500
            throw new java.io.UncheckedIOException(e);
        }
    }

    /**
     * 获取最新上传的音乐
     */
    private List<LatestMusicItem> getLatestMusic() {
        List<LatestMusicItem> latestMusic = new ArrayList<>();

        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            String sql = """
                SELECT id, title, artist, album, duration, language, tags, file_format, created_at, max_quality
                FROM music
                ORDER BY created_at DESC
                LIMIT ?
                """;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, LATEST_LIMIT);

                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    int id = rs.getInt("id");
                    latestMusic.add(new LatestMusicItem(
                            id,
                            rs.getString("title"),
                            rs.getString("artist"),
                            rs.getString("album"),
                            rs.getInt("duration"),
                            null,
                            MusicAssetLocator.coverApiUrl(id),
                            rs.getString("language"),
                            rs.getString("tags"),
                            rs.getString("file_format"),
                            rs.getTimestamp("created_at").getTime(),
                            rs.getString("max_quality")));
                }
            }
            logger.debug("成功获取最新音乐，共 {} 条记录", latestMusic.size());
        } catch (Exception e) {
            logger.error("获取最新音乐时出错", e);
        }

        return latestMusic;
    }

    // 最新音乐项：只读数据载体（字段名与顺序与旧 POJO 一致，序列化结果不变）
    public record LatestMusicItem(
            int id,
            String title,
            String artist,
            String album,
            int duration,
            String coverPath,
            String coverUrl,
            String language,
            String tags,
            String fileFormat,
            long createdAt,
            String maxQuality) {
    }

    // 最新音乐响应
    private record LatestMusicResponse(boolean success, String message, List<LatestMusicItem> data) {
    }
}
