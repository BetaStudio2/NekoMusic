package com.neko.music.handlers;

import com.neko.music.util.UserLookup;

import com.google.gson.JsonObject;
import com.neko.music.Main;
import com.neko.music.model.Playlist;
import com.neko.music.service.PlaylistService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

@WebServlet("/api/playlist/*")
public class GetPlaylistDetailHandler extends ApiServlet {
    private static final Logger logger = LoggerFactory.getLogger(GetPlaylistDetailHandler.class);
    private PlaylistService playlistService;

    @Override
    public void init() throws ServletException {
        playlistService = Main.getPlaylistService();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");

        logger.info("收到获取歌单详情请求");

        // 从URL路径中获取歌单ID
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "缺少歌单ID");
            return;
        }

        try {
            // 提取歌单ID（路径格式：/api/playlist/{playlistId}）
            String playlistIdStr = pathInfo.substring(1); // 去掉开头的 /
            Integer playlistId = Integer.parseInt(playlistIdStr);

            // 获取歌单详情
            Optional<Playlist> playlistOpt = playlistService.getPlaylistById(playlistId);

            if (!playlistOpt.isPresent()) {
                sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "歌单不存在");
                return;
            }

            Playlist playlist = playlistOpt.get();

            logger.info("获取到歌单详情: playlistId={}", playlistId);

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("message", "获取歌单详情成功");

            JsonObject playlistJson = new JsonObject();
            playlistJson.addProperty("id", playlist.id());
            playlistJson.addProperty("userId", playlist.userId());
            playlistJson.addProperty("name", playlist.name());
            playlistJson.addProperty("description", playlist.description());
            playlistJson.addProperty("musicCount", playlist.musicCount());
            playlistJson.addProperty("createdAt", playlist.createdAt());
            playlistJson.addProperty("updatedAt", playlist.updatedAt());
            
            // 添加创建者信息
            JsonObject creator = new JsonObject();
            creator.addProperty("id", playlist.userId());
            creator.addProperty("username", UserLookup.getUserName(playlist.userId()));
            playlistJson.add("creator", creator);
            
            response.add("playlist", playlistJson);

            sendSuccessResponse(resp, response);
        } catch (NumberFormatException e) {
            logger.error("歌单ID格式错误: {}", pathInfo);
            sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "歌单ID格式错误");
        } catch (Exception e) {
            logger.error("处理获取歌单详情请求时发生错误: {}", e.getMessage(), e);
            sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "服务器内部错误");
        }
    }

    /**
     * 发送成功响应
     */

    /**
     * 发送错误响应
     */
    
    /**
     * 获取昵称
     */
}