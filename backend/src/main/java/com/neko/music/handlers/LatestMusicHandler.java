package com.neko.music.handlers;

import com.neko.music.Main;
import com.neko.music.util.HttpResourceCache;
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

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 返回条数固定，不接受任何请求参数：本接口响应声明为可被共享缓存（public），
        // 若内容随查询串变化、而边缘缓存的缓存键又不含查询串，同一份缓存条目就会被
        // 其它查询串的请求写坏（Web 缓存投毒）。公开缓存接口只允许存在唯一变体。
        List<LatestMusicItem> latestMusic = getLatestMusic();

        response.setStatus(HttpStatus.OK_200);
        response.setContentType("application/json;charset=utf-8");
        response.setHeader("Cache-Control",
                "public, max-age=" + HttpResourceCache.MAX_AGE_HALF_HOUR);
        // message 固定用源语言：本接口是 public 共享缓存，文案随 Accept-Language 变化会让缓存
        // 按语言碎片化（要么加 Vary，要么保持唯一变体）。客户端本来也不依赖这句文案。
        LatestMusicResponse latestMusicResponse = new LatestMusicResponse(true, ApiMessages.source("music.latest_ok"), latestMusic);
        response.getWriter().println(Main.getObjectMapper().writeValueAsString(latestMusicResponse));
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
            logger.info("成功获取最新音乐，共 {} 条记录", latestMusic.size());
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
