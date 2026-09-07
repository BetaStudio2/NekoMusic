package com.neko.music.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.neko.music.Main;
import com.neko.music.service.NeteaseCloudMusicClient;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** 兼容 NeteaseCloudMusicApi 常用只读接口的后端入口。 */
public class NeteaseCloudMusicHandler extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        String path = request.getPathInfo();
        if (path == null || path.isBlank() || "/".equals(path)) {
            writeError(response, HttpServletResponse.SC_NOT_FOUND, "缺少网易云接口路径");
            return;
        }

        try {
            NeteaseCloudMusicClient client = Main.getNeteaseCloudMusicClient();
            JsonNode result = switch (path) {
                case "/search" -> client.searchRaw(request.getParameter("keywords"), intParam(request, "type", 1, 1, 1014),
                        intParam(request, "limit", 30, 1, 100), intParam(request, "offset", 0, 0, 100000));
                case "/song/detail" -> client.fetchSongDetailRaw(parseIds(request.getParameter("ids")));
                case "/song/url/v1" -> client.fetchSongUrlRaw(longParam(request, "id"), request.getParameter("level"));
                case "/lyric" -> client.fetchLyricRaw(longParam(request, "id"));
                case "/playlist/detail" -> client.fetchPlaylistDetail(longParam(request, "id"));
                case "/playlist/track/all" -> client.fetchPlaylistTrackAll(longParam(request, "id"),
                        intParam(request, "limit", 0, 0, 5000), intParam(request, "offset", 0, 0, 5000));
                default -> null;
            };
            if (result == null) {
                writeError(response, HttpServletResponse.SC_NOT_FOUND, "不支持的网易云接口路径");
                return;
            }
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(Main.getObjectMapper().writeValueAsString(result));
        } catch (IllegalArgumentException e) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            writeError(response, HttpServletResponse.SC_BAD_GATEWAY, "网易云接口请求失败");
        }
    }

    private static long longParam(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        if (value == null || !value.matches("[0-9]{1,20}")) throw new IllegalArgumentException("缺少有效的 " + name);
        try { return Long.parseLong(value); } catch (NumberFormatException e) { throw new IllegalArgumentException("参数超出范围: " + name); }
    }

    private static int intParam(HttpServletRequest request, String name, int fallback, int min, int max) {
        String value = request.getParameter(name);
        if (value == null || value.isBlank()) return fallback;
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) throw new IllegalArgumentException(name + " 超出允许范围");
            return parsed;
        } catch (NumberFormatException e) { throw new IllegalArgumentException(name + " 必须是数字"); }
    }

    private static List<Long> parseIds(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("缺少 ids");
        String[] parts = raw.split(",");
        if (parts.length > 1000) throw new IllegalArgumentException("ids 一次最多 1000 个");
        List<Long> ids = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (!part.trim().matches("[0-9]{1,20}")) throw new IllegalArgumentException("ids 必须是逗号分隔的数字");
            ids.add(Long.parseLong(part.trim()));
        }
        return ids;
    }

    private static void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.getWriter().write(Main.getObjectMapper().createObjectNode()
                .put("code", status).put("msg", message == null ? "请求失败" : message).toString());
    }
}
