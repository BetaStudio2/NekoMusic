package com.neko.music.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
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

public class LatestMusicHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(LatestMusicHandler.class);
    private ObjectMapper objectMapper = new ObjectMapper();

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
        response.getWriter().println(objectMapper.writeValueAsString(latestMusicResponse));
    }

    /**
     * 获取最新上传的音乐
     */
    private List<LatestMusicItem> getLatestMusic(int limit) {
        List<LatestMusicItem> latestMusic = new ArrayList<>();

        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            String sql = """
                SELECT id, title, artist, album, duration, cover_path, language, tags, file_format, created_at
                FROM music
                ORDER BY created_at DESC
                LIMIT ?
                """;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, limit);

                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    LatestMusicItem item = new LatestMusicItem();
                    item.setId(rs.getInt("id"));
                    item.setTitle(rs.getString("title"));
                    item.setArtist(rs.getString("artist"));
                    item.setAlbum(rs.getString("album"));
                    item.setDuration(rs.getInt("duration"));
                    item.setCoverPath(rs.getString("cover_path"));
                    item.setLanguage(rs.getString("language"));
                    item.setTags(rs.getString("tags"));
                    item.setFileFormat(rs.getString("file_format"));
                    item.setCreatedAt(rs.getTimestamp("created_at").getTime());

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

                    latestMusic.add(item);
                }
            }
            logger.info("成功获取最新音乐，共 {} 条记录", latestMusic.size());
        } catch (Exception e) {
            logger.error("获取最新音乐时出错", e);
        }

        return latestMusic;
    }

    // 内部类用于表示最新音乐项
    public static class LatestMusicItem {
        private int id;
        private String title;
        private String artist;
        private String album;
        private int duration;
        private String coverPath;
        private String coverUrl;
        private String language;
        private String tags;
        private String fileFormat;
        private long createdAt;

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
        public String getFileFormat() { return fileFormat; }
        public void setFileFormat(String fileFormat) { this.fileFormat = fileFormat; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    }

    // 内部类用于表示最新音乐响应
    private static class LatestMusicResponse {
        private boolean success;
        private String message;
        private List<LatestMusicItem> data;

        public LatestMusicResponse(boolean success, String message, List<LatestMusicItem> data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public List<LatestMusicItem> getData() { return data; }
        public void setData(List<LatestMusicItem> data) { this.data = data; }
    }
}