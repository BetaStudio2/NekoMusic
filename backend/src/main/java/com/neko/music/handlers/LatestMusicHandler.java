package com.neko.music.handlers;

import com.neko.music.Main;
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

public class LatestMusicHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(LatestMusicHandler.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 获取查询参数 limit（限制返回数量，默认300）
        String limitStr = request.getParameter("limit");
        int limit = 300;
        if (limitStr != null && !limitStr.isEmpty()) {
            try {
                limit = Integer.parseInt(limitStr);
                if (limit <= 0) limit = 300;
                if (limit > 500) limit = 500; // 最大限制500条
            } catch (NumberFormatException e) {
                // 使用默认值
            }
        }

        // 获取最新上传的音乐
        List<LatestMusicItem> latestMusic = getLatestMusic(limit);

        response.setStatus(HttpStatus.OK_200);
        response.setContentType("application/json;charset=utf-8");
        LatestMusicResponse latestMusicResponse = new LatestMusicResponse(true, "获取最新音乐成功", latestMusic);
        response.getWriter().println(Main.getObjectMapper().writeValueAsString(latestMusicResponse));
    }

    /**
     * 获取最新上传的音乐
     */
    private List<LatestMusicItem> getLatestMusic(int limit) {
        List<LatestMusicItem> latestMusic = new ArrayList<>();

        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            String sql = """
                SELECT id, title, artist, album, duration, language, tags, file_format, created_at
                FROM music
                ORDER BY created_at DESC
                LIMIT ?
                """;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, limit);

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
                            rs.getTimestamp("created_at").getTime()));
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
            long createdAt) {
    }

    // 最新音乐响应
    private record LatestMusicResponse(boolean success, String message, List<LatestMusicItem> data) {
    }
}