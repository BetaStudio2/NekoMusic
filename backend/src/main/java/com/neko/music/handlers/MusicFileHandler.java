package com.neko.music.handlers;

import com.google.gson.JsonObject;
import com.neko.music.service.MusicQualityService;
import com.neko.music.util.HttpResourceCache;
import com.neko.music.util.MusicLookup;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * 音乐播放地址签发：{@code GET /api/music/file/{id}?quality=...}。
 *
 * <p>返回 JSON {@code {"success":true,"data":{"url":"/media/music/..."}}}，由播放器 / 下载逻辑再取。</p>
 *
 * <p>本接口是「有服务端成本」的只读接口：每次调用都会触发音质解析（可能转码）。它由
 * {@code ReplayProtectionFilter} 统一要求一次性 {@code X-Neko-Nonce}，因此抓包重放同一个请求
 * 只能成功一次，重放会得到 409。返回的 {@code /media/music/...} 仍是固定地址、由 CDN 共享缓存，
 * 本接口不改变媒体缓存语义。</p>
 *
 * <p>为什么不再 302：{@code <audio src>} 无法携带自定义请求头，302 会把校验绕过去；返回 JSON
 * 让调用方用 {@code fetch}（可带头）换取地址。旧的 {@code /{id}/challenge}、{@code /{id}/resolve}
 * 两步协议已由通用 nonce 机制取代。</p>
 */
public class MusicFileHandler extends ApiServlet {

    private static final Logger logger = LoggerFactory.getLogger(MusicFileHandler.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", HttpResourceCache.CACHE_CONTROL_NO_STORE);

        Integer musicId = parseMusicId(request.getPathInfo());
        if (musicId == null) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "无效的音乐ID");
            return;
        }
        if (!MusicLookup.musicRowExists(musicId)) {
            sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, "音乐文件不存在");
            return;
        }

        String audioPath;
        try {
            audioPath = MusicQualityService.resolveAudio(musicId, request.getParameter("quality"));
        } catch (Exception e) {
            logger.error("解析音乐音质失败 id={}", musicId, e);
            sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, "音乐文件不存在或音质处理失败");
            return;
        }

        JsonObject data = new JsonObject();
        data.addProperty("url", MusicQualityService.publicUrl(musicId, audioPath));

        JsonObject body = new JsonObject();
        body.addProperty("success", true);
        body.addProperty("message", "");
        body.add("data", data);
        sendSuccessResponse(response, body);
    }

    /** 解析 /{id}；非法返回 null。 */
    private static Integer parseMusicId(String pathInfo) {
        if (pathInfo == null) {
            return null;
        }
        String raw = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        if (raw.isEmpty() || raw.contains("/")) {
            return null;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
