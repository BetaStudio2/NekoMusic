package com.neko.music.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.neko.music.Main;
import com.neko.music.util.MusicAssetLocator;
import com.neko.music.service.UserAuthService;
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
import java.util.LinkedHashSet;
import java.util.List;

public class UserFavoriteHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(UserFavoriteHandler.class);
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // 获取收藏列表
        String token = req.getHeader("Authorization");
        
        if (token == null || token.isEmpty()) {
            sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "未提供认证令牌");
            return;
        }
        
        // 验证token并获取用户ID
        Integer userId = validateToken(token);
        if (userId == null) {
            sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "无效的认证令牌");
            return;
        }
        
        try {
            List<JsonObject> favorites = getUserFavorites(userId);
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.add("favorites", Main.getGson().toJsonTree(favorites));
            
            sendSuccessResponse(resp, response);
        } catch (Exception e) {
            logger.error("获取收藏列表失败: {}", e.getMessage(), e);
            sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "获取收藏列表失败");
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // 添加收藏
        String token = req.getHeader("Authorization");
        
        if (token == null || token.isEmpty()) {
            sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "未提供认证令牌");
            return;
        }
        
        // 验证token并获取用户ID
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

        List<Integer> musicIds = new ArrayList<>();
        if (requestBody != null) {
            if (requestBody.has("musicId") && !requestBody.get("musicId").isJsonNull()) {
                musicIds.add(requestBody.get("musicId").getAsInt());
            }
            if (requestBody.has("musicIds")) {
                if (!requestBody.get("musicIds").isJsonArray()) {
                    sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "musicIds 必须是 JSON 数组");
                    return;
                }
                JsonArray arr = requestBody.getAsJsonArray("musicIds");
                for (JsonElement el : arr) {
                    if (el != null && !el.isJsonNull()) {
                        musicIds.add(el.getAsInt());
                    }
                }
            }
        }

        List<Integer> toAdd = new ArrayList<>(new LinkedHashSet<>(musicIds));
        if (toAdd.isEmpty()) {
            sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "音乐ID不能为空（可传 musicId 或 musicIds 数组）");
            return;
        }

        try {
            List<Integer> failed = new ArrayList<>();
            for (int mid : toAdd) {
                if (!addFavorite(userId, mid)) {
                    failed.add(mid);
                }
            }

            if (failed.isEmpty()) {
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("addedCount", toAdd.size());
                if (toAdd.size() == 1) {
                    response.addProperty("message", "收藏成功");
                } else {
                    response.addProperty("message", "已收藏 " + toAdd.size() + " 首音乐");
                }
                sendSuccessResponse(resp, response);
            } else {
                int addedCount = toAdd.size() - failed.size();
                JsonObject response = new JsonObject();
                response.addProperty("success", false);
                response.addProperty("addedCount", addedCount);
                JsonArray failedArr = new JsonArray();
                for (int f : failed) {
                    failedArr.add(f);
                }
                response.add("failedMusicIds", failedArr);
                response.addProperty("message", "部分音乐未能收藏（已存在或收藏失败），失败数量: " + failed.size());

                resp.setContentType("application/json");
                resp.setCharacterEncoding("UTF-8");
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                try (PrintWriter writer = resp.getWriter()) {
                    writer.print(response.toString());
                    writer.flush();
                }
            }
        } catch (Exception e) {
            logger.error("添加收藏失败: {}", e.getMessage(), e);
            sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "添加收藏失败");
        }
    }
    
    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // 删除收藏
        String token = req.getHeader("Authorization");
        
        if (token == null || token.isEmpty()) {
            sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "未提供认证令牌");
            return;
        }
        
        // 验证token并获取用户ID
        Integer userId = validateToken(token);
        if (userId == null) {
            sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "无效的认证令牌");
            return;
        }
        
        // 从路径中获取音乐ID
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "缺少音乐ID");
            return;
        }
        
        try {
            int musicId = Integer.parseInt(pathInfo.substring(1));
            boolean success = removeFavorite(userId, musicId);
            
            if (success) {
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("message", "取消收藏成功");
                sendSuccessResponse(resp, response);
            } else {
                sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "取消收藏失败");
            }
        } catch (NumberFormatException e) {
            sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "无效的音乐ID");
        } catch (Exception e) {
            logger.error("删除收藏失败: {}", e.getMessage(), e);
            sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "删除收藏失败");
        }
    }
    
    private Integer validateToken(String token) {
        // 使用 UserAuthService 走 Redis 缓存，避免每次查DB
        return Main.getUserAuthService().validateToken(token).orElse(null);
    }
    
    private List<JsonObject> getUserFavorites(int userId) throws SQLException {
        List<JsonObject> favorites = new ArrayList<>();
        String sql = "SELECT m.id, m.title, m.artist, m.album, m.duration " +
                     "FROM user_favorites uf " +
                     "JOIN music m ON uf.music_id = m.id " +
                     "WHERE uf.user_id = ? " +
                     "ORDER BY uf.created_at DESC";
        
        try (Connection conn = Main.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                JsonObject music = new JsonObject();
                music.addProperty("id", rs.getInt("id"));
                music.addProperty("title", rs.getString("title"));
                music.addProperty("artist", rs.getString("artist"));
                music.addProperty("album", rs.getString("album"));
                music.addProperty("duration", rs.getInt("duration"));
                music.addProperty("filename", MusicAssetLocator.fileApiUrl(rs.getInt("id")));
                favorites.add(music);
            }
        }
        
        return favorites;
    }
    
    private boolean addFavorite(int userId, int musicId) throws SQLException {
        // 使用 INSERT IGNORE 避免竞态条件：如果已存在则静默跳过
        String sql = "INSERT IGNORE INTO user_favorites (user_id, music_id, created_at) VALUES (?, ?, NOW())";

        try (Connection conn = Main.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            stmt.setInt(2, musicId);

            return stmt.executeUpdate() > 0;
        }
    }
    
    private boolean removeFavorite(int userId, int musicId) throws SQLException {
        String sql = "DELETE FROM user_favorites WHERE user_id = ? AND music_id = ?";
        
        try (Connection conn = Main.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            stmt.setInt(2, musicId);
            
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