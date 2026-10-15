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

public class MusicRankingHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(MusicRankingHandler.class);

    /** 固定返回条数（客户端不可调）。 */
    private static final int RANKING_LIMIT = 200;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 返回条数固定，不接受任何请求参数：本接口响应声明为可被共享缓存（public），
        // 若内容随查询串变化、而边缘缓存的缓存键又不含查询串，同一份缓存条目就会被
        // 其它查询串的请求写坏（Web 缓存投毒）。公开缓存接口只允许存在唯一变体。
        List<MusicRankingItem> ranking = getMusicRanking();

        response.setStatus(HttpStatus.OK_200);
        response.setContentType("application/json;charset=utf-8");
        response.setHeader("Cache-Control",
                "public, max-age=" + HttpResourceCache.MAX_AGE_HALF_HOUR);
        // message 固定用源语言：public 共享缓存接口保持唯一变体，理由见 LatestMusicHandler。
        RankingResponse rankingResponse = new RankingResponse(true, ApiMessages.source("music.ranking_ok"), ranking);
        response.getWriter().println(Main.getObjectMapper().writeValueAsString(rankingResponse));
    }

    /**
     * 获取播放次数排行榜
     */
    private List<MusicRankingItem> getMusicRanking() {
        List<MusicRankingItem> ranking = new ArrayList<>();

        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            String sql = """
                SELECT id, title, artist, album, duration, language, tags, play_count, max_quality
                FROM music
                WHERE play_count > 0
                ORDER BY play_count DESC
                LIMIT ?
                """;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, RANKING_LIMIT);

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
                            rs.getInt("play_count"),
                            rs.getString("max_quality")));
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
            int playCount,
            String maxQuality) {
    }

    // 排行榜响应
    private record RankingResponse(boolean success, String message, List<MusicRankingItem> data) {
    }
}
