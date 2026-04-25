package com.neko.music.handlers;

import com.google.gson.JsonArray;
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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

@WebServlet("/api/user/playlist/music/*")
public class GetPlaylistMusicHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(GetPlaylistMusicHandler.class);
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

        logger.info("收到获取歌单音乐列表请求");

        // 从URL路径中获取歌单ID
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "缺少歌单ID");
            return;
        }

        try {
            // 提取歌单ID（路径格式：/api/user/playlist/music/{playlistId}）
            String playlistIdStr = pathInfo.substring(1); // 去掉开头的 /
            Integer playlistId = Integer.parseInt(playlistIdStr);

            // 检查歌单是否存在
            if (!playlistService.getPlaylistById(playlistId).isPresent()) {
                sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "歌单不存在");
                return;
            }

            // 获取歌单中的音乐列表（允许未登录访问）
            List<JsonObject> musicList = playlistService.getPlaylistMusic(playlistId);

            logger.info("获取到 {} 首音乐: playlistId={}", musicList.size(), playlistId);

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("message", "获取歌单音乐列表成功");
            response.addProperty("playlistId", playlistId);

            JsonArray musicArray = new JsonArray();
            for (JsonObject music : musicList) {
                musicArray.add(music);
            }

            response.add("musicList", musicArray);
            response.addProperty("total", musicList.size());

            sendSuccessResponse(resp, response);
        } catch (NumberFormatException e) {
            logger.error("歌单ID格式错误: {}", pathInfo);
            sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "歌单ID格式错误");
        } catch (Exception e) {
            logger.error("处理获取歌单音乐列表请求时发生错误: {}", e.getMessage(), e);
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