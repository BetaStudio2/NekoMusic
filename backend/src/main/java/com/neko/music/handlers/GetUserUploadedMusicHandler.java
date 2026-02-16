package com.neko.music.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.neko.music.Main;
import com.neko.music.service.UserAuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@WebServlet("/api/user/uploaded-music")
public class GetUserUploadedMusicHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(GetUserUploadedMusicHandler.class);
    private final Gson gson = new Gson();
    private UserAuthService userAuthService;

    @Override
    public void init() throws ServletException {
        userAuthService = Main.getUserAuthService();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "POST, GET, PUT, DELETE, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        logger.info("收到获取用户上传审核通过的音乐请求");

        try {
            // 验证用户身份
            String token = req.getHeader("Authorization");
            if (token == null || token.isEmpty()) {
                sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "用户未登录或token无效");
                return;
            }

            // 移除Bearer前缀（如果有）
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            Integer userId = userAuthService.validateToken(token).orElse(null);
            if (userId == null) {
                sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "用户未登录或token无效");
                return;
            }

            // 获取用户上传审核通过的音乐列表
            List<JsonObject> musicList = getUserApprovedMusic(userId);

            logger.info("获取到 {} 首审核通过的音乐: userId={}", musicList.size(), userId);

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("message", "获取用户上传审核通过的音乐列表成功");
            response.addProperty("userId", userId);

            JsonArray musicArray = new JsonArray();
            for (JsonObject music : musicList) {
                musicArray.add(music);
            }

            response.add("musicList", musicArray);
            response.addProperty("total", musicList.size());

            String jsonResponse = gson.toJson(response);
            logger.info("响应数据: {}", jsonResponse);
            sendSuccessResponse(resp, response);
        } catch (Exception e) {
            logger.error("处理获取用户上传审核通过的音乐请求时发生错误: {}", e.getMessage(), e);
            sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "服务器内部错误");
        }
    }

    /**
     * 获取用户上传审核通过的音乐列表
     */
    private List<JsonObject> getUserApprovedMusic(int userId) {
        List<JsonObject> musicList = new ArrayList<>();
        String sql = """
            SELECT m.* 
            FROM music m
            WHERE m.upload_user_id = ?
            ORDER BY m.created_at DESC
            """;

        try (Connection conn = Main.getDatabaseManager().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                JsonObject music = new JsonObject();
                music.addProperty("id", rs.getInt("id"));
                music.addProperty("title", rs.getString("title"));
                music.addProperty("artist", rs.getString("artist"));
                music.addProperty("album", rs.getString("album"));
                music.addProperty("duration", rs.getInt("duration"));
                music.addProperty("language", rs.getString("language"));
                music.addProperty("tags", rs.getString("tags"));
                music.addProperty("fileFormat", rs.getString("file_format"));
                
                Timestamp createdAt = rs.getTimestamp("created_at");
                if (createdAt != null) {
                    music.addProperty("createdAt", createdAt.toLocalDateTime().toString());
                }

                musicList.add(music);
            }

        } catch (SQLException e) {
            logger.error("获取用户上传审核通过的音乐失败: {}", e.getMessage());
        }

        return musicList;
    }

    /**
     * 发送成功响应
     */
    private void sendSuccessResponse(HttpServletResponse resp, JsonObject response) throws IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
        String jsonResponse = gson.toJson(response);
        logger.info("发送成功响应: {}", jsonResponse);
        try (PrintWriter out = resp.getWriter()) {
            out.print(jsonResponse);
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
            out.print(gson.toJson(response));
            out.flush();
        }
    }
}