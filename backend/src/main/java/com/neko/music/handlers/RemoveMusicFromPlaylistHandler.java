package com.neko.music.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.neko.music.Main;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@WebServlet("/api/user/playlist/music/remove")
public class RemoveMusicFromPlaylistHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(RemoveMusicFromPlaylistHandler.class);
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

        logger.info("收到从歌单中移除音乐的请求");

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

            Integer playlistId = null;
            List<Integer> musicIds = new ArrayList<>();

            if (requestData != null) {
                if (requestData.has("playlistId") && !requestData.get("playlistId").isJsonNull()) {
                    playlistId = requestData.get("playlistId").getAsInt();
                }
                if (requestData.has("musicId") && !requestData.get("musicId").isJsonNull()) {
                    musicIds.add(requestData.get("musicId").getAsInt());
                }
                if (requestData.has("musicIds")) {
                    if (!requestData.get("musicIds").isJsonArray()) {
                        sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "musicIds 必须是 JSON 数组");
                        return;
                    }
                    JsonArray arr = requestData.getAsJsonArray("musicIds");
                    for (JsonElement el : arr) {
                        if (el != null && !el.isJsonNull()) {
                            musicIds.add(el.getAsInt());
                        }
                    }
                }
            }

            // 验证请求参数
            if (playlistId == null) {
                sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "歌单ID不能为空");
                return;
            }

            List<Integer> toRemove = new ArrayList<>(new LinkedHashSet<>(musicIds));
            if (toRemove.isEmpty()) {
                sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "音乐ID不能为空（可传 musicId 或 musicIds 数组）");
                return;
            }

            // 检查用户是否是歌单的所有者
            if (!playlistService.isPlaylistOwner(playlistId, userId)) {
                logger.warn("用户尝试从不属于自己的歌单中移除音乐: userId={}, playlistId={}", userId, playlistId);
                sendErrorResponse(resp, HttpServletResponse.SC_FORBIDDEN, "无权限修改此歌单");
                return;
            }

            List<Integer> failed = new ArrayList<>();
            for (int mid : toRemove) {
                if (!playlistService.removeMusicFromPlaylist(playlistId, mid)) {
                    failed.add(mid);
                }
            }

            if (failed.isEmpty()) {
                logger.info("音乐从歌单中移除成功: playlistId={}, musicIds={}, userId={}", playlistId, toRemove, userId);

                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("removedCount", toRemove.size());
                if (toRemove.size() == 1) {
                    response.addProperty("message", "音乐从歌单中移除成功");
                } else {
                    response.addProperty("message", "已从歌单中移除 " + toRemove.size() + " 首音乐");
                }

                sendSuccessResponse(resp, response);
            } else {
                logger.warn("音乐从歌单中移除部分或全部失败: playlistId={}, failed={}, userId={}", playlistId, failed, userId);

                int removedCount = toRemove.size() - failed.size();
                JsonObject response = new JsonObject();
                response.addProperty("success", false);
                response.addProperty("removedCount", removedCount);
                JsonArray failedArr = new JsonArray();
                for (int f : failed) {
                    failedArr.add(f);
                }
                response.add("failedMusicIds", failedArr);
                response.addProperty("message", "部分音乐未能从歌单中移除（不在歌单中或移除失败），失败数量: " + failed.size());

                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.setContentType("application/json;charset=UTF-8");
                try (PrintWriter out = resp.getWriter()) {
                    out.print(Main.getGson().toJson(response));
                    out.flush();
                }
            }

        } catch (Exception e) {
            logger.error("处理从歌单中移除音乐请求时发生错误: {}", e.getMessage(), e);
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