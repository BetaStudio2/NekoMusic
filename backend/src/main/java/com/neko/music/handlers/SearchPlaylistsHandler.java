package com.neko.music.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.neko.music.Main;
import com.neko.music.service.PlaylistService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

@WebServlet("/api/playlists/search")
public class SearchPlaylistsHandler extends ApiServlet {
    private static final Logger logger = LoggerFactory.getLogger(SearchPlaylistsHandler.class);
    private PlaylistService playlistService;

    @Override
    public void init() throws ServletException {
        playlistService = Main.getPlaylistService();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");

        logger.info("收到搜索歌单请求");

        try {
            // 读取请求体
            String requestBody = readBody(req);

            JsonObject requestData = Main.getGson().fromJson(requestBody.toString(), JsonObject.class);

            if (requestData == null || !requestData.has("query")) {
                sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "缺少搜索关键词");
                return;
            }

            String query = requestData.get("query").getAsString().trim();

            if (query.isEmpty()) {
                sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "搜索关键词不能为空");
                return;
            }

            // 搜索歌单
            List<JsonObject> playlists = playlistService.searchPlaylists(query);

            logger.info("搜索到 {} 个歌单: query={}", playlists.size(), query);

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("message", "搜索成功");
            response.addProperty("total", playlists.size());

            JsonArray playlistsArray = new JsonArray();
            for (JsonObject playlist : playlists) {
                playlistsArray.add(playlist);
            }

            response.add("results", playlistsArray);

            sendSuccessResponse(resp, response);
        } catch (Exception e) {
            logger.error("处理搜索歌单请求时发生错误: {}", e.getMessage(), e);
            sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "服务器内部错误");
        }
    }

    /**
     * 发送成功响应
     */

    /**
     * 发送错误响应
     */
}