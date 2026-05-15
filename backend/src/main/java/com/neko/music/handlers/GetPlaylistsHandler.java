package com.neko.music.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonNull;
import com.neko.music.Main;
import com.neko.music.model.Playlist;
import com.neko.music.service.PlaylistService;
import com.neko.music.service.UserAuthService;
import com.neko.music.util.VipUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@WebServlet("/api/user/playlists")
public class GetPlaylistsHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(GetPlaylistsHandler.class);
    private PlaylistService playlistService;
    private UserAuthService userAuthService;

    @Override
    public void init() throws ServletException {
        playlistService = Main.getPlaylistService();
        userAuthService = Main.getUserAuthService();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "POST, GET, PUT, DELETE, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        logger.info("收到获取歌单列表请求");

        // 获取token
        String token = req.getHeader("Authorization");

        if (token == null || token.isEmpty()) {
            sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "未提供认证令牌");
            return;
        }

        // 验证token并获取用户ID
        Integer userId = userAuthService.validateToken(token).orElse(null);
        if (userId == null) {
            sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "无效的认证令牌");
            return;
        }

        try {
            // 获取当前用户的歌单列表
            List<Playlist> playlists = playlistService.getUserPlaylists(userId);

            logger.info("获取到 {} 个歌单: userId={}", playlists.size(), userId);

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("message", "获取歌单列表成功");

            JsonArray playlistsArray = new JsonArray();
            for (Playlist playlist : playlists) {
                JsonObject playlistJson = new JsonObject();
                playlistJson.addProperty("id", playlist.getId());
                playlistJson.addProperty("userId", playlist.getUserId());
                playlistJson.addProperty("name", playlist.getName());
                playlistJson.addProperty("description", playlist.getDescription());
                playlistJson.addProperty("musicCount", playlist.getMusicCount());
                playlistJson.addProperty("createdAt", playlist.getCreatedAt());
                playlistJson.addProperty("updatedAt", playlist.getUpdatedAt());
                
                // 添加创建者信息
                JsonObject creator = new JsonObject();
                creator.addProperty("id", playlist.getUserId());
                creator.addProperty("username", getUserName(playlist.getUserId()));
                playlistJson.add("creator", creator);
                
                playlistsArray.add(playlistJson);
            }

            response.add("playlists", playlistsArray);

            // 与登录接口一致：附带当前用户 VIP 状态（便于前端复用本接口刷新，无需单独路由）
            Optional<Timestamp> vipOpt = userAuthService.findVipExpiresAtByUserId(userId);
            Timestamp vip = vipOpt.orElse(null);
            response.addProperty("isVip", VipUtil.isVipActiveNow(vip));
            if (vip != null) {
                response.addProperty("vipExpiresAt", vip.toInstant().toString());
            } else {
                response.add("vipExpiresAt", JsonNull.INSTANCE);
            }

            sendSuccessResponse(resp, response);
        } catch (Exception e) {
            logger.error("处理获取歌单列表请求时发生错误: {}", e.getMessage(), e);
            sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "服务器内部错误");
        }
    }

    /**
     * 发送成功响应
     */
    private void sendSuccessResponse(HttpServletResponse resp, JsonObject response) throws IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
        try (PrintWriter out = resp.getWriter()) {
            out.print(Main.getGson().toJson(response));
            out.flush();
        }
    }

    /**
     * 发送错误响应
     */
    private void sendErrorResponse(HttpServletResponse resp, int statusCode, String message) throws IOException {
        resp.setStatus(statusCode);
        JsonObject response = new JsonObject();
        response.addProperty("success", false);
        response.addProperty("message", message);

        try (PrintWriter out = resp.getWriter()) {
            out.print(Main.getGson().toJson(response));
            out.flush();
        }
    }
    
    /**
     * 获取用户名
     */
    private String getUserName(int userId) {
        String sql = "SELECT username FROM users WHERE id = ?";
        
        try (Connection conn = Main.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getString("username");
            }
        } catch (SQLException e) {
            logger.error("获取用户名失败: {}", e.getMessage(), e);
        }
        
        return "未知用户";
    }
}