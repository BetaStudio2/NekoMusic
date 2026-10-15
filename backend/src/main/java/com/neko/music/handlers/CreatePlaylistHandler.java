package com.neko.music.handlers;

import com.neko.music.i18n.ApiMessages;
import com.google.gson.JsonObject;
import com.neko.music.Main;
import com.neko.music.model.Playlist;
import com.neko.music.service.PlaylistService;
import com.neko.music.service.UserAuthService;
import com.neko.music.util.SensitiveWordUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

@WebServlet("/api/user/playlist/create")
public class CreatePlaylistHandler extends ApiServlet {
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

        logger.info("收到创建歌单请求");

        // 获取token
        String token = req.getHeader("Authorization");

        if (token == null || token.isEmpty()) {
            sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "auth.token_missing");
            return;
        }

        // 验证token并获取用户ID
        Integer userId = userAuthService.validateToken(token).orElse(null);
        if (userId == null) {
            sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "auth.invalid_bearer");
            return;
        }

        // 读取请求体
        String requestBody = readBody(req);

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
                sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "playlist.name_required");
                return;
            }

            if (name.length() > 255) {
                sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "playlist.name_too_long");
                return;
            }

            if (description != null && description.length() > 500) {
                sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "playlist.description_too_long");
                return;
            }

            // 验证违禁词
            if (SensitiveWordUtil.contains(name)) {
                sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "playlist.name_contains_banned");
                return;
            }
            if (description != null && !description.trim().isEmpty() && SensitiveWordUtil.contains(description)) {
                sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "playlist.description_contains_banned");
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
                logger.info("歌单创建成功: id={}, userId={}, name={}", playlist.id(), userId, playlist.name());

                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("message", ApiMessages.translate("playlist.create_ok"));

                JsonObject playlistData = new JsonObject();
                playlistData.addProperty("id", playlist.id());
                playlistData.addProperty("name", playlist.name());
                playlistData.addProperty("description", playlist.description());
                playlistData.addProperty("musicCount", playlist.musicCount());
                playlistData.addProperty("createdAt", playlist.createdAt());
                playlistData.addProperty("updatedAt", playlist.updatedAt());

                response.add("playlist", playlistData);

                sendSuccessResponse(resp, response);
            } else {
                logger.warn("歌单创建失败: userId={}, name={}", userId, name);
                sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "playlist.create_failed");
            }

        } catch (Exception e) {
            logger.error("处理创建歌单请求时发生错误: {}", e.getMessage(), e);
            sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "common.internal_error");
        }
    }

    /**
     * 发送成功响应
     */

    /**
     * 发送错误响应
     */
}