package com.neko.music.handlers;

import com.neko.music.i18n.ApiMessages;
import com.google.gson.JsonObject;
import com.neko.music.Main;
import com.neko.music.service.PlaylistService;
import com.neko.music.service.UserAuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@WebServlet("/api/user/playlist/delete")
public class DeletePlaylistHandler extends ApiServlet {
    private static final Logger logger = LoggerFactory.getLogger(DeletePlaylistHandler.class);
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

        logger.info("收到删除歌单请求");

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

            Integer playlistId = null;

            if (requestData != null && requestData.has("id")) {
                playlistId = requestData.get("id").getAsInt();
            }

            // 验证请求参数
            if (playlistId == null) {
                sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "playlist.id_required");
                return;
            }

            // 检查用户是否是歌单的所有者
            if (!playlistService.isPlaylistOwner(playlistId, userId)) {
                logger.warn("用户尝试删除不属于自己的歌单: userId={}, playlistId={}", userId, playlistId);
                sendErrorResponse(resp, HttpServletResponse.SC_FORBIDDEN, "playlist.no_permission_delete");
                return;
            }

            // 删除歌单
            boolean success = playlistService.deletePlaylist(playlistId);

            if (success) {
                logger.info("歌单删除成功: id={}, userId={}", playlistId, userId);

                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("message", ApiMessages.translate("playlist.delete_ok"));

                sendSuccessResponse(resp, response);
            } else {
                logger.warn("歌单删除失败: id={}, userId={}", playlistId, userId);
                sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "playlist.delete_failed");
            }

        } catch (Exception e) {
            logger.error("处理删除歌单请求时发生错误: {}", e.getMessage(), e);
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