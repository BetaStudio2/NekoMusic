package com.neko.music.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.neko.music.Main;
import com.neko.music.service.QQMusicClient;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * QQ 音乐歌单详情代理，挂载在 {@code /loser/qq/getSongListDetail}。
 *
 * <p>响应包装与 qq-music-api-next 的 getSongListDetail 保持一致：歌单数据放在
 * {@code response} 字段中，结构为 {@code {code, subcode, msg, cdlist[]}}。
 * 该接口只返回元数据，不含可下载直链。</p>
 */
public class QQMusicSongListDetailHandler extends ApiServlet {
    private static final Logger logger = LoggerFactory.getLogger(QQMusicSongListDetailHandler.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        setJsonHeaders(response);

        String disstid = request.getParameter("disstid");
        if (disstid == null || !disstid.matches("[0-9]{1,19}")) {
            sendErrorObject(response, HttpServletResponse.SC_BAD_REQUEST,
                    "缺少有效的 disstid（歌单 ID 必须为数字）");
            return;
        }

        try {
            JsonNode upstreamJson = Main.getQQMusicClient().fetchPlaylistDetailRaw(disstid);
            ObjectNode result = Main.getObjectMapper().createObjectNode();
            result.set("response", upstreamJson);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(Main.getObjectMapper().writeValueAsString(result));
        } catch (QQMusicClient.UpstreamException e) {
            logger.warn("QQ 音乐歌单接口返回 HTTP {}，disstid={}", e.getStatusCode(), disstid);
            sendErrorObject(response, HttpServletResponse.SC_BAD_GATEWAY,
                    "QQ 音乐接口请求失败（HTTP " + e.getStatusCode() + "）");
        } catch (IOException e) {
            logger.warn("请求 QQ 音乐歌单接口失败，disstid={}: {}", disstid, e.getMessage());
            sendErrorObject(response, HttpServletResponse.SC_BAD_GATEWAY,
                    e.getMessage() == null ? "请求 QQ 音乐接口失败" : e.getMessage());
        }
    }

    private static void setJsonHeaders(HttpServletResponse response) {
        response.setContentType("application/json;charset=UTF-8");
    }

}
