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

public class MusicRankingHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(MusicRankingHandler.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 获取查询参数 limit（限制返回数量，默认200）
        String limitStr = request.getParameter("limit");
        int limit = 200;
        if (limitStr != null && !limitStr.isEmpty()) {
            try {
                limit = Integer.parseInt(limitStr);
                if (limit <= 0) limit = 200;
                if (limit > 200) limit = 200; // 最大限制200条
            } catch (NumberFormatException e) {
                // 使用默认值
            }
        }

        // 获取排行榜
        List<MusicRankingItem> ranking = getMusicRanking(limit);

        response.setStatus(HttpStatus.OK_200);
        response.setContentType("application/json;charset=utf-8");
        RankingResponse rankingResponse = new RankingResponse(true, "获取播放次数排行榜成功", ranking);
        response.getWriter().println(Main.getObjectMapper().writeValueAsString(rankingResponse));
    }

    /**
     * 获取播放次数排行榜
     */
    private List<MusicRankingItem> getMusicRanking(int limit) {
        List<MusicRankingItem> ranking = new ArrayList<>();

        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            String sql = """
                SELECT id, title, artist, album, duration, language, tags, play_count
                FROM music
                WHERE play_count > 0
                ORDER BY play_count DESC
                LIMIT ?
                """;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, limit);

                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    int id = rs.getInt("id");
                    ranking.add(new MusicRankingItem(
                            id,
                            rs.getString("title"),
                            rs.getString("artist"),
                            rs.getString("album"),
                            rs.getInt("duration"),
                            null,
                            MusicAssetLocator.coverApiUrl(id),
                            rs.getString("language"),
                            rs.getString("tags"),
                            rs.getInt("play_count")));
                }
            }
            logger.info("成功获取播放次数排行榜，共 {} 条记录", ranking.size());
        } catch (Exception e) {
            logger.error("获取播放次数排行榜时出错", e);
        }

        return ranking;
    }

    // 排行榜音乐项：只读数据载体（字段名与顺序与旧 POJO 一致，序列化结果不变）
    public record MusicRankingItem(
            int id,
            String title,
            String artist,
            String album,
            int duration,
            String coverPath,
            String coverUrl,
            String language,
            String tags,
            int playCount) {
    }

    // 排行榜响应
    private record RankingResponse(boolean success, String message, List<MusicRankingItem> data) {
    }
}