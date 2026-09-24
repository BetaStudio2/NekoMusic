package com.neko.music.handlers;

import com.neko.music.Main;
import com.neko.music.service.QishuiMusicClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** 汽水公开歌单详情代理；不提供汽水账号登录或 Cookie 管理接口。 */
public class QishuiPlaylistDetailHandler extends ApiServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handle(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handle(request, response);
    }

    private void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String input = firstNonBlank(
                request.getParameter("playlist_id"),
                request.getParameter("url"),
                request.getParameter("id"));
        if (input == null || input.isBlank() || input.length() > 2048) {
            sendErrorData(response, HttpServletResponse.SC_BAD_REQUEST,
                    "缺少有效的 playlist_id（汽水歌单 ID 或分享链接）");
            return;
        }

        try {
            QishuiMusicClient client = Main.getExternalImportService().getQishuiMusicClient();
            sendResponse(response, true, "ok", client.fetchPlaylistDetailRaw(input.trim()));
        } catch (IllegalArgumentException e) {
            sendErrorData(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (IOException e) {
            sendErrorData(response, HttpServletResponse.SC_BAD_GATEWAY, e.getMessage());
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }
}
