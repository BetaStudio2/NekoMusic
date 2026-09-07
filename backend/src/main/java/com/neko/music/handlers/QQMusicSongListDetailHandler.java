package com.neko.music.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.neko.music.Main;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * QQ 音乐歌单详情代理。
 *
 * <p>请求参数和响应包装与 qq-music-api-next 的 getSongListDetail 保持一致：
 * 上游响应放在 {@code response} 字段中。</p>
 */
public class QQMusicSongListDetailHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(QQMusicSongListDetailHandler.class);
    private static final String UPSTREAM = "https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        setJsonHeaders(response);

        String disstid = request.getParameter("disstid");
        if (disstid == null || !disstid.matches("[0-9]{1,32}")) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "缺少有效的 disstid（歌单 ID 必须为数字）");
            return;
        }

        String upstreamUrl = buildUpstreamUrl(disstid);
        HttpRequest upstreamRequest = HttpRequest.newBuilder()
                .uri(URI.create(upstreamUrl))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "https://c.y.qq.com/")
                .header("User-Agent", "Mozilla/5.0 (NekoMusic QQMusic playlist proxy)")
                .GET()
                .build();

        try {
            HttpResponse<String> upstreamResponse = httpClient.send(
                    upstreamRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (upstreamResponse.statusCode() < 200 || upstreamResponse.statusCode() >= 300) {
                logger.warn("QQ 音乐歌单接口返回 HTTP {}，disstid={}", upstreamResponse.statusCode(), disstid);
                writeError(response, HttpServletResponse.SC_BAD_GATEWAY,
                        "QQ 音乐接口请求失败（HTTP " + upstreamResponse.statusCode() + "）");
                return;
            }

            JsonNode upstreamJson = Main.getObjectMapper().readTree(upstreamResponse.body());
            if (upstreamJson == null || upstreamJson.isMissingNode()) {
                writeError(response, HttpServletResponse.SC_BAD_GATEWAY, "QQ 音乐接口返回为空");
                return;
            }

            ObjectNode result = Main.getObjectMapper().createObjectNode();
            result.set("response", upstreamJson);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(Main.getObjectMapper().writeValueAsString(result));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("请求 QQ 音乐歌单接口被中断，disstid={}", disstid);
            writeError(response, HttpServletResponse.SC_GATEWAY_TIMEOUT, "请求 QQ 音乐接口被中断");
        } catch (Exception e) {
            logger.warn("请求 QQ 音乐歌单接口失败，disstid={}: {}", disstid, e.getMessage());
            writeError(response, HttpServletResponse.SC_BAD_GATEWAY, "请求 QQ 音乐接口失败");
        }
    }

    private static String buildUpstreamUrl(String disstid) {
        String encodedDisstid = URLEncoder.encode(disstid, StandardCharsets.UTF_8);
        return UPSTREAM
                + "?format=json"
                + "&outCharset=utf-8"
                + "&type=1"
                + "&json=1"
                + "&utf8=1"
                + "&onlysong=0"
                + "&new_format=1"
                + "&g_tk=1124214810"
                + "&loginUin=0"
                + "&hostUin=0"
                + "&inCharset=utf8"
                + "&notice=0"
                + "&platform=yqq.json"
                + "&needNewCode=0"
                + "&disstid=" + encodedDisstid;
    }

    private static void setJsonHeaders(HttpServletResponse response) {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private static void writeError(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        ObjectNode error = Main.getObjectMapper().createObjectNode();
        error.put("error", message);
        response.getWriter().write(Main.getObjectMapper().writeValueAsString(error));
    }
}
