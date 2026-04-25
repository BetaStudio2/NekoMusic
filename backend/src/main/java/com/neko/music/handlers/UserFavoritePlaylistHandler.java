package com.neko.music.handlers;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.neko.music.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserFavoritePlaylistHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(UserFavoritePlaylistHandler.class);
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String token = req.getHeader("Authorization");
        
        if (token == null || token.isEmpty()) {
            sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "未提供认证令牌");
            return;
        }
        
        Integer userId = validateToken(token);
        if (userId == null) {
            sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "无效的认证令牌");
            return;
        }
        
        // 检查是否请求特定收藏歌单内的音乐
        String pathInfo = req.getPathInfo();
        if (pathInfo != null && !pathInfo.equals("/")) {
            // 获取收藏歌单内的音乐列表
            try {
                int playlistId = Integer.parseInt(pathInfo.substring(1));
                List<JsonObject> musicList = getPlaylistMusic(userId, playlistId);
                
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.add("music", Main.getGson().toJsonTree(musicList));
                
                sendSuccessResponse(resp, response);
            } catch (NumberFormatException e) {
                sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "无效的歌单ID");
            } catch (SQLException e) {
                logger.error("获取歌单音乐失败: {}", e.getMessage(), e);
                sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "获取歌单音乐失败");
            }
        } else {
            // 获取收藏歌单列表
            try {
                List<JsonObject> playlists = getFavoritePlaylists(userId);
                
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.add("playlists", Main.getGson().toJsonTree(playlists));
                
                sendSuccessResponse(resp, response);
            } catch (SQLException e) {
                logger.error("获取收藏歌单列表失败: {}", e.getMessage(), e);
                sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "获取收藏歌单列表失败");
            }
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String token = req.getHeader("Authorization");
        
        if (token == null || token.isEmpty()) {
            sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "未提供认证令牌");
            return;
        }
        
        Integer userId = validateToken(token);
        if (userId == null) {
            sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "无效的认证令牌");
            return;
        }
        
        // 读取请求体
        StringBuilder sb = new StringBuilder();
        String line;
        try (BufferedReader reader = req.getReader()) {
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        
        JsonObject requestBody = Main.getGson().fromJson(sb.toString(), JsonObject.class);
        int playlistId = requestBody.get("playlistId").getAsInt();
        
        try {
            boolean success = addFavoritePlaylist(userId, playlistId);
            if (success) {
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("message", "收藏歌单成功");
                sendSuccessResponse(resp, response);
            } else {
                sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "收藏歌单失败或已收藏");
            }
        } catch (SQLException e) {
            logger.error("收藏歌单失败: {}", e.getMessage(), e);
            sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "收藏歌单失败");
        }
    }
    
    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String token = req.getHeader("Authorization");
        
        if (token == null || token.isEmpty()) {
            sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "未提供认证令牌");
            return;
        }
        
        Integer userId = validateToken(token);
        if (userId == null) {
            sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "无效的认证令牌");
            return;
        }
        
        // 从路径中获取歌单ID
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "缺少歌单ID");
            return;
        }
        
        try {
            int playlistId = Integer.parseInt(pathInfo.substring(1));
            boolean success = removeFavoritePlaylist(userId, playlistId);
            
            if (success) {
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("message", "取消收藏歌单成功");
                sendSuccessResponse(resp, response);
            } else {
                sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "取消收藏歌单失败");
            }
        } catch (NumberFormatException e) {
            sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "无效的歌单ID");
        } catch (SQLException e) {
            logger.error("取消收藏歌单失败: {}", e.getMessage(), e);
            sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "取消收藏歌单失败");
        }
    }
    
    private Integer validateToken(String token) {
        String sql = "SELECT user_id FROM user_tokens WHERE token = ? AND expires_at > NOW()";
        
        try (Connection conn = Main.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, token);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt("user_id");
            }
        } catch (SQLException e) {
            logger.error("验证token失败: {}", e.getMessage(), e);
        }
        
        return null;
    }
    
    private List<JsonObject> getFavoritePlaylists(int userId) throws SQLException {
        List<JsonObject> playlists = new ArrayList<>();
        String sql = "SELECT p.id, p.name, p.description, p.music_count, p.created_at, p.updated_at, " +
                     "u.username as creator_name, u.id as creator_id, " +
                     "ufp.created_at as favorite_time " +
                     "FROM user_favorite_playlists ufp " +
                     "JOIN playlists p ON ufp.playlist_id = p.id " +
                     "JOIN users u ON p.user_id = u.id " +
                     "WHERE ufp.user_id = ? " +
                     "ORDER BY ufp.created_at DESC";
        
        try (Connection conn = Main.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                JsonObject playlist = new JsonObject();
                playlist.addProperty("id", rs.getInt("id"));
                playlist.addProperty("name", rs.getString("name"));
                playlist.addProperty("description", rs.getString("description"));
                playlist.addProperty("musicCount", rs.getInt("music_count"));
                playlist.addProperty("createdAt", rs.getTimestamp("created_at").getTime());
                playlist.addProperty("updatedAt", rs.getTimestamp("updated_at").getTime());
                playlist.addProperty("favoriteTime", rs.getTimestamp("favorite_time").getTime());
                
                JsonObject creator = new JsonObject();
                creator.addProperty("id", rs.getInt("creator_id"));
                creator.addProperty("username", rs.getString("creator_name"));
                playlist.add("creator", creator);
                
                playlists.add(playlist);
            }
        }
        
        return playlists;
    }
    
    private List<JsonObject> getPlaylistMusic(int userId, int playlistId) throws SQLException {
        // 首先验证用户是否收藏了这个歌单
        String checkSql = "SELECT COUNT(*) FROM user_favorite_playlists WHERE user_id = ? AND playlist_id = ?";
        
        try (Connection conn = Main.getDatabaseManager().getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            
            checkStmt.setInt(1, userId);
            checkStmt.setInt(2, playlistId);
            ResultSet rs = checkStmt.executeQuery();
            
            if (!rs.next() || rs.getInt(1) == 0) {
                throw new SQLException("用户未收藏该歌单");
            }
        }
        
        // 获取歌单内的音乐
        List<JsonObject> musicList = new ArrayList<>();
        String sql = "SELECT m.id, m.title, m.artist, m.album, m.duration, m.file_path, pm.position " +
                     "FROM playlist_music pm " +
                     "JOIN music m ON pm.music_id = m.id " +
                     "WHERE pm.playlist_id = ? " +
                     "ORDER BY pm.position ASC";
        
        try (Connection conn = Main.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, playlistId);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                JsonObject music = new JsonObject();
                music.addProperty("id", rs.getInt("id"));
                music.addProperty("title", rs.getString("title"));
                music.addProperty("artist", rs.getString("artist"));
                music.addProperty("album", rs.getString("album"));
                music.addProperty("duration", rs.getInt("duration"));
                music.addProperty("filename", rs.getString("file_path"));
                music.addProperty("position", rs.getInt("position"));
                musicList.add(music);
            }
        }
        
        return musicList;
    }
    
    private boolean addFavoritePlaylist(int userId, int playlistId) throws SQLException {
        // 检查是否已经收藏
        String checkSql = "SELECT COUNT(*) FROM user_favorite_playlists WHERE user_id = ? AND playlist_id = ?";
        
        try (Connection conn = Main.getDatabaseManager().getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            
            checkStmt.setInt(1, userId);
            checkStmt.setInt(2, playlistId);
            ResultSet rs = checkStmt.executeQuery();
            
            if (rs.next() && rs.getInt(1) > 0) {
                return false; // 已经收藏
            }
        }
        
        // 添加收藏
        String sql = "INSERT INTO user_favorite_playlists (user_id, playlist_id, created_at) VALUES (?, ?, NOW())";
        
        try (Connection conn = Main.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            stmt.setInt(2, playlistId);
            
            return stmt.executeUpdate() > 0;
        }
    }
    
    private boolean removeFavoritePlaylist(int userId, int playlistId) throws SQLException {
        String sql = "DELETE FROM user_favorite_playlists WHERE user_id = ? AND playlist_id = ?";
        
        try (Connection conn = Main.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            stmt.setInt(2, playlistId);
            
            return stmt.executeUpdate() > 0;
        }
    }
    
    private void sendSuccessResponse(HttpServletResponse resp, JsonObject response) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setStatus(HttpServletResponse.SC_OK);
        
        try (PrintWriter writer = resp.getWriter()) {
            writer.print(response.toString());
            writer.flush();
        }
    }
    
    private void sendErrorResponse(HttpServletResponse resp, int statusCode, String message) throws IOException {
        JsonObject response = new JsonObject();
        response.addProperty("success", false);
        response.addProperty("message", message);
        
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setStatus(statusCode);
        
        try (PrintWriter writer = resp.getWriter()) {
            writer.print(response.toString());
            writer.flush();
        }
    }
}