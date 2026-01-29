package com.neko.music.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.neko.music.Main;
import com.neko.music.model.Playlist;
import com.neko.music.service.PlaylistService;
import com.neko.music.service.UserAuthService;
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

@WebServlet("/api/user/playlist/update")
public class UpdatePlaylistHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(UpdatePlaylistHandler.class);
    private final Gson gson = new Gson();
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

        logger.info("收到更新歌单请求");

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
            JsonObject requestData = gson.fromJson(requestBody.toString(), JsonObject.class);

            Integer playlistId = null;
            String name = null;
            String description = null;

            if (requestData != null) {
                if (requestData.has("id")) {
                    playlistId = requestData.get("id").getAsInt();
                }
                if (requestData.has("name")) {
                    name = requestData.get("name").getAsString();
                }
                if (requestData.has("description")) {
                    description = requestData.get("description").getAsString();
                }
            }

            // 验证请求参数
            if (playlistId == null) {
                sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "歌单ID不能为空");
                return;
            }

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

            // 检查用户是否是歌单的所有者
            if (!playlistService.isPlaylistOwner(playlistId, userId)) {
                logger.warn("用户尝试更新不属于自己的歌单: userId={}, playlistId={}", userId, playlistId);
                sendErrorResponse(resp, HttpServletResponse.SC_FORBIDDEN, "无权限修改此歌单");
                return;
            }

            // 更新歌单
            boolean success = playlistService.updatePlaylist(
                playlistId,
                name.trim(),
                description != null ? description.trim() : null
            );

            if (success) {
                logger.info("歌单更新成功: id={}, userId={}", playlistId, userId);

                // 获取更新后的歌单信息
                Optional<Playlist> playlistOpt = playlistService.getPlaylistById(playlistId);
                if (playlistOpt.isPresent()) {
                    Playlist playlist = playlistOpt.get();

                    JsonObject response = new JsonObject();
                    response.addProperty("success", true);
                    response.addProperty("message", "歌单更新成功");

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
                    sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "歌单更新成功但无法获取更新后的信息");
                }
            } else {
                logger.warn("歌单更新失败: id={}, userId={}", playlistId, userId);
                sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "歌单更新失败");
            }

        } catch (Exception e) {
            logger.error("处理更新歌单请求时发生错误: {}", e.getMessage(), e);
            sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "服务器内部错误");
        }
    }

    /**
     * 发送成功响应
     */
    private void sendSuccessResponse(HttpServletResponse resp, JsonObject response) throws IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
        try (PrintWriter out = resp.getWriter()) {
            out.print(gson.toJson(response));
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