package com.neko.music.handlers;

import com.neko.music.Main;
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
                SELECT id, title, artist, album, duration, cover_path, language, tags, play_count
                FROM music
                WHERE play_count > 0
                ORDER BY play_count DESC
                LIMIT ?
                """;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, limit);

                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    MusicRankingItem item = new MusicRankingItem();
                    item.setId(rs.getInt("id"));
                    item.setTitle(rs.getString("title"));
                    item.setArtist(rs.getString("artist"));
                    item.setAlbum(rs.getString("album"));
                    item.setDuration(rs.getInt("duration"));
                    item.setCoverPath(rs.getString("cover_path"));
                    item.setLanguage(rs.getString("language"));
                    item.setTags(rs.getString("tags"));
                    item.setPlayCount(rs.getInt("play_count"));

                    // 设置封面URL
                    String coverPath = item.getCoverPath();
                    if (coverPath == null || coverPath.isEmpty()) {
                        item.setCoverUrl("/api/defaultIcon");
                    } else {
                        // 检查封面文件是否存在
                        java.nio.file.Path path = java.nio.file.Paths.get(coverPath);
                        if (java.nio.file.Files.exists(path)) {
                            item.setCoverUrl(coverPath);
                        } else {
                            item.setCoverUrl("/api/defaultIcon");
                        }
                    }

                    ranking.add(item);
                }
            }
            logger.info("成功获取播放次数排行榜，共 {} 条记录", ranking.size());
        } catch (Exception e) {
            logger.error("获取播放次数排行榜时出错", e);
        }

        return ranking;
    }

    // 内部类用于表示排行榜音乐项
    public static class MusicRankingItem {
        private int id;
        private String title;
        private String artist;
        private String album;
        private int duration;
        private String coverPath;
        private String coverUrl;
        private String language;
        private String tags;
        private int playCount;

        // Getters and Setters
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getArtist() { return artist; }
        public void setArtist(String artist) { this.artist = artist; }
        public String getAlbum() { return album; }
        public void setAlbum(String album) { this.album = album; }
        public int getDuration() { return duration; }
        public void setDuration(int duration) { this.duration = duration; }
        public String getCoverPath() { return coverPath; }
        public void setCoverPath(String coverPath) { this.coverPath = coverPath; }
        public String getCoverUrl() { return coverUrl; }
        public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public String getTags() { return tags; }
        public void setTags(String tags) { this.tags = tags; }
        public int getPlayCount() { return playCount; }
        public void setPlayCount(int playCount) { this.playCount = playCount; }
    }

    // 内部类用于表示排行榜响应
    private static class RankingResponse {
        private boolean success;
        private String message;
        private List<MusicRankingItem> data;

        public RankingResponse(boolean success, String message, List<MusicRankingItem> data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public List<MusicRankingItem> getData() { return data; }
        public void setData(List<MusicRankingItem> data) { this.data = data; }
    }
}