package com.neko.music.handlers;

import com.google.gson.JsonObject;
import com.neko.music.Main;
import com.neko.music.model.Playlist;
import com.neko.music.service.PlaylistService;
import com.neko.music.service.UserAuthService;
import com.neko.music.util.SensitiveWordUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Optional;

@WebServlet("/api/user/playlist/create")
public class CreatePlaylistHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(CreatePlaylistHandler.class);
    private PlaylistService playlistService;
    private UserAuthService userAuthService;

    @Override
    public void init() throws ServletException {
        playlistService = Main.getPlaylistService();
        userAuthService = Main.getUserAuthService();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "POST, GET, PUT, DELETE, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        logger.info("收到创建歌单请求");

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

        // 读取请求体
        StringBuilder requestBody = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                requestBody.append(line);
            }
        }

        try {
            JsonObject requestData = Main.getGson().fromJson(requestBody.toString(), JsonObject.class);

            String name = null;
            String description = null;

            if (requestData != null) {
                if (requestData.has("name")) {
                    name = requestData.get("name").getAsString();
                }
                if (requestData.has("description")) {
                    description = requestData.get("description").getAsString();
                }
            }

            // 验证请求参数
            if (name == null || name.trim().isEmpty()) {
                sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "歌单名称不能为空");
                return;
            }

            if (name.length() > 255) {
                sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "歌单名称过长");
                return;
            }

            if (description != null && description.length() > 500) {
                sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "歌单描述过长");
                return;
            }

            // 验证违禁词
            if (SensitiveWordUtil.contains(name)) {
                sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "歌单名称包含违禁词");
                return;
            }
            if (description != null && !description.trim().isEmpty() && SensitiveWordUtil.contains(description)) {
                sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "歌单描述包含违禁词");
                return;
            }

            // 创建歌单
            Optional<Playlist> playlistOpt = playlistService.createPlaylist(
                userId,
                name.trim(),
                description != null ? description.trim() : null
            );

            if (playlistOpt.isPresent()) {
                Playlist playlist = playlistOpt.get();
                logger.info("歌单创建成功: id={}, userId={}, name={}", playlist.getId(), userId, playlist.getName());

                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("message", "歌单创建成功");

                JsonObject playlistData = new JsonObject();
                playlistData.addProperty("id", playlist.getId());
                playlistData.addProperty("name", playlist.getName());
                playlistData.addProperty("description", playlist.getDescription());
                playlistData.addProperty("musicCount", playlist.getMusicCount());
                playlistData.addProperty("createdAt", playlist.getCreatedAt());
                playlistData.addProperty("updatedAt", playlist.getUpdatedAt());

                response.add("playlist", playlistData);

                sendSuccessResponse(resp, response);
            } else {
                logger.warn("歌单创建失败: userId={}, name={}", userId, name);
                sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "歌单创建失败");
            }

        } catch (Exception e) {
            logger.error("处理创建歌单请求时发生错误: {}", e.getMessage(), e);
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
}