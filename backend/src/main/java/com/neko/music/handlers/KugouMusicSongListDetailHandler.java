package com.neko.music.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.neko.music.Main;
import com.neko.music.service.KugouMusicClient;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * 酷狗音乐歌单详情代理，挂载在 {@code /loser/kugou/getSongListDetail}。
 *
 * <p>响应包装：歌单数据放在 {@code response} 字段中，结构为
 * {@code {code, listid, name, songnum, songlist[]}}。该接口只返回元数据，不含可下载直链。</p>
 */
public class KugouMusicSongListDetailHandler extends ApiServlet {
    private static final Logger logger = LoggerFactory.getLogger(KugouMusicSongListDetailHandler.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        setJsonHeaders(response);

        String listId = request.getParameter("listid");
        if (listId == null || listId.isBlank()) {
            sendErrorObject(response, HttpServletResponse.SC_BAD_REQUEST,
                    "缺少有效的 listid（酷狗歌单链接或 ID）");
            return;
        }

        try {
            JsonNode upstreamJson = Main.getKugouMusicClient().fetchPlaylistDetailRaw(listId);
            ObjectNode result = Main.getObjectMapper().createObjectNode();
            result.set("response", upstreamJson);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(Main.getObjectMapper().writeValueAsString(result));
        } catch (KugouMusicClient.UpstreamException e) {
            logger.warn("酷狗歌单接口返回 HTTP {}，listid={}", e.getStatusCode(), listId);
            sendErrorObject(response, HttpServletResponse.SC_BAD_GATEWAY,
                    "酷狗接口请求失败（HTTP " + e.getStatusCode() + "）");
        } catch (IOException e) {
            logger.warn("请求酷狗歌单接口失败，listid={}: {}", listId, e.getMessage());
            sendErrorObject(response, HttpServletResponse.SC_BAD_GATEWAY,
                    e.getMessage() == null ? "请求酷狗接口失败" : e.getMessage());
        }
    }

    private static void setJsonHeaders(HttpServletResponse response) {
        response.setContentType("application/json;charset=UTF-8");
    }

}
