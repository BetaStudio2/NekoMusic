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

public class MusicInfoHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(MusicInfoHandler.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        
        if (pathInfo == null || pathInfo.equals("/")) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("音乐ID不能为空");
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
            return;
        }
        
        // 解析音乐ID (路径格式: /{id})
        String idStr = pathInfo.replace("/", "");
        int musicId;
        
        try {
            musicId = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("无效的音乐ID");
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
            return;
        }
        
        // 获取音乐信息（无需管理员权限）
        Music music = getMusicById(musicId);
        
        if (music == null) {
            response.setStatus(HttpStatus.NOT_FOUND_404);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("音乐不存在");
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
            return;
        }
        
        response.setStatus(HttpStatus.OK_200);
        response.setContentType("application/json;charset=utf-8");
        MusicResponse musicResponse = new MusicResponse(true, "获取音乐详情成功", music);
        response.getWriter().println(Main.getObjectMapper().writeValueAsString(musicResponse));
    }
    
    /**
     * 根据ID获取音乐信息（无需管理员权限）
     */
    private Music getMusicById(int musicId) {
        Music music = null;
        
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            String sql = "SELECT id, title, artist, album, duration, language, tags, upload_user_id, created_at, updated_at FROM music WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, musicId);
                
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    music = new Music();
                    music.setId(rs.getInt("id"));
                    music.setTitle(rs.getString("title"));
                    music.setArtist(rs.getString("artist"));
                    music.setAlbum(rs.getString("album"));
                    music.setDuration(rs.getInt("duration"));
                    music.setFilePath(MusicAssetLocator.fileApiUrl(music.getId()));
                    music.setCoverFilePath(MusicAssetLocator.coverApiUrl(music.getId()));
                    music.setLanguage(rs.getString("language"));
                    music.setTags(rs.getString("tags"));
                    music.setUploadUserId(rs.getInt("upload_user_id"));
                    music.setCreatedAt(rs.getTimestamp("created_at").toString());
                    music.setUpdatedAt(rs.getTimestamp("updated_at").toString());
                }
            }
        } catch (Exception e) {
            logger.error("获取音乐详情时出错", e);
        }
        
        return music;
    }

    // 内部类用于表示音乐对象
    public static class Music {
        private int id;
        private String title;
        private String artist;
        private String album;
        private int duration; // 时长，单位秒
        private String filePath;
        private String coverFilePath; // 封面路径
        private String language; // 语言
        private String tags; // 标签
        private int uploadUserId;
        private String createdAt;
        private String updatedAt;
        
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
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        public String getCoverFilePath() { return coverFilePath; }
        public void setCoverFilePath(String coverFilePath) { this.coverFilePath = coverFilePath; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public String getTags() { return tags; }
        public void setTags(String tags) { this.tags = tags; }
        public int getUploadUserId() { return uploadUserId; }
        public void setUploadUserId(int uploadUserId) { this.uploadUserId = uploadUserId; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public String getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
        
        /** 统一走封面接口（无封面时由 {@link MusicCoverHandler} 返回默认图），避免详情接口扫盘。 */
        public String getCoverUrl() {
            if (id <= 0) {
                return "/api/defaultIcon";
            }
            return MusicAssetLocator.coverApiUrl(id);
        }
    }
    
    // 内部类用于表示单个音乐响应
    private static class MusicResponse {
        private boolean success;
        private String message;
        private Music data;
        
        public MusicResponse(boolean success, String message, Music data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public Music getData() { return data; }
        public void setData(Music data) { this.data = data; }
    }
    
    // 内部类用于表示错误响应
    private static class ErrorResponse {
        private String error;
        
        public ErrorResponse(String error) {
            this.error = error;
        }
        
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }
}