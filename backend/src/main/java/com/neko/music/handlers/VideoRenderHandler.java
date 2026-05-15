package com.neko.music.handlers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.neko.music.Main;
import com.neko.music.config.ConfigManager;
import com.neko.music.model.VideoRenderJob;
import com.neko.music.service.VideoRenderQuotaService;
import com.neko.music.service.VideoRenderService;
import com.neko.music.util.HttpResourceCache;
import com.neko.music.util.VideoRenderPaths;
import com.neko.music.util.VipUtil;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.regex.Pattern;

/**
 * 横屏短视频渲染（异步队列，立即返回 jobId）：
 * <ul>
 *   <li>POST /api/video/render/create — 创建任务</li>
 *   <li>GET /api/video/render/{jobId} — 查询状态</li>
 *   <li>GET /api/video/render/{jobId}/download — 下载成片</li>
 * </ul>
 */
public class VideoRenderHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(VideoRenderHandler.class);
    private static final Pattern JOB_ID_PATTERN = Pattern.compile("^[0-9a-fA-F-]{36}$");

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setCharacterEncoding("UTF-8");
        if (!isCreatePath(req)) {
            sendJson(resp, HttpServletResponse.SC_NOT_FOUND, false, "接口不存在");
            return;
        }
        handleCreate(req, resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setCharacterEncoding("UTF-8");
        String pathInfo = normalizePathInfo(req.getPathInfo());
        if (pathInfo == null || pathInfo.isEmpty()) {
            sendJson(resp, HttpServletResponse.SC_NOT_FOUND, false, "接口不存在");
            return;
        }
        if (pathInfo.endsWith("/download")) {
            String jobId = pathInfo.substring(0, pathInfo.length() - "/download".length());
            if (jobId.startsWith("/")) {
                jobId = jobId.substring(1);
            }
            handleDownload(req, resp, jobId);
            return;
        }
        String jobId = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        handleStatus(req, resp, jobId);
    }

    private void handleCreate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        ConfigManager cfg = Main.getConfigManager();
        if (!cfg.isVideoRenderEnabled()) {
            sendJson(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, false, "视频生成功能未启用");
            return;
        }

        Optional<Integer> userIdOpt = requireUser(req);
        if (userIdOpt.isEmpty()) {
            sendJson(resp, HttpServletResponse.SC_UNAUTHORIZED, false, "请先登录");
            return;
        }
        int userId = userIdOpt.get();

        String body = readBody(req);
        JsonObject root;
        try {
            root = JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception e) {
            sendJson(resp, HttpServletResponse.SC_BAD_REQUEST, false, "请求体不是合法 JSON");
            return;
        }
        if (root == null || !root.has("musicId") || !root.get("musicId").isJsonPrimitive()) {
            sendJson(resp, HttpServletResponse.SC_BAD_REQUEST, false, "缺少 musicId");
            return;
        }
        int musicId = root.get("musicId").getAsInt();
        if (musicId <= 0) {
            sendJson(resp, HttpServletResponse.SC_BAD_REQUEST, false, "musicId 无效");
            return;
        }

        double startSec = 0;
        if (root.has("startSec") && root.get("startSec").isJsonPrimitive()) {
            startSec = root.get("startSec").getAsDouble();
            if (startSec < 0) {
                sendJson(resp, HttpServletResponse.SC_BAD_REQUEST, false, "startSec 不能为负数");
                return;
            }
        }

        Optional<MusicMeta> metaOpt = loadMusicMeta(musicId);
        if (metaOpt.isEmpty()) {
            sendJson(resp, HttpServletResponse.SC_NOT_FOUND, false, "音乐不存在");
            return;
        }
        MusicMeta meta = metaOpt.get();

        Optional<Path> audioOpt = VideoRenderService.resolveAudio(musicId);
        if (audioOpt.isEmpty()) {
            sendJson(resp, HttpServletResponse.SC_NOT_FOUND, false, "音频文件不存在");
            return;
        }

        Timestamp vipExpires = Main.getUserAuthService().findVipExpiresAtByUserId(userId).orElse(null);
        boolean isVip = VipUtil.isVipActiveNow(vipExpires);

        int trackDuration = meta.durationSec > 0 ? meta.durationSec : 300;
        if (startSec >= trackDuration) {
            sendJson(resp, HttpServletResponse.SC_BAD_REQUEST, false, "startSec 超出歌曲时长");
            return;
        }

        double clipDuration;
        boolean watermarked;
        Integer remainingToday = null;

        if (isVip) {
            clipDuration = trackDuration - startSec;
            watermarked = false;
        } else {
            int limit = cfg.getVideoRenderNonVipDailyLimit();
            int maxSec = cfg.getVideoRenderNonVipMaxDurationSec();
            VideoRenderQuotaService quota = Main.getVideoRenderQuotaService();
            int remaining = quota.reserveDailySlot(userId);
            if (remaining < 0) {
                sendJson(resp, 429, false,
                        "今日免费生成次数已用完（" + limit + " 次/天），开通 VIP 可无限制使用");
                return;
            }
            remainingToday = remaining;
            clipDuration = Math.min(maxSec, trackDuration - startSec);
            watermarked = true;
        }

        if (clipDuration <= 0.5) {
            sendJson(resp, HttpServletResponse.SC_BAD_REQUEST, false, "可渲染时长过短");
            return;
        }

        String jobId = UUID.randomUUID().toString();
        VideoRenderJob job = new VideoRenderJob();
        job.setId(jobId);
        job.setUserId(userId);
        job.setMusicId(musicId);
        job.setStartSec(startSec);
        job.setDurationSec(clipDuration);
        job.setWatermarked(watermarked);

        try {
            Main.getVideoRenderDatabaseManager().insertPending(job);
        } catch (Exception e) {
            logger.error("创建视频任务失败 userId={} musicId={}", userId, musicId, e);
            sendJson(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, false, "创建任务失败");
            return;
        }

        Optional<Path> coverOpt = VideoRenderService.resolveCover(musicId);
        try {
            Main.getVideoRenderService().submit(job, meta.title, meta.artist, audioOpt.get(), coverOpt);
        } catch (RejectedExecutionException e) {
            jobDbMarkFailed(jobId, "渲染队列已满，请稍后再试");
            sendJson(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, false, "渲染队列已满，请稍后再试");
            return;
        }

        JsonObject data = new JsonObject();
        data.addProperty("jobId", jobId);
        data.addProperty("status", "pending");
        data.addProperty("isVip", isVip);
        data.addProperty("durationSec", clipDuration);
        data.addProperty("watermarked", watermarked);
        data.addProperty("musicId", musicId);
        if (remainingToday != null) {
            data.addProperty("remainingToday", remainingToday);
        }

        JsonObject out = new JsonObject();
        out.addProperty("success", true);
        out.addProperty("message", "任务已创建，正在后台渲染");
        out.add("data", data);
        sendRawJson(resp, HttpServletResponse.SC_ACCEPTED, out);
    }

    private static void jobDbMarkFailed(String jobId, String message) {
        try {
            Main.getVideoRenderDatabaseManager().markFailed(jobId, message);
        } catch (Exception ignored) {
        }
    }

    private void handleStatus(HttpServletRequest req, HttpServletResponse resp, String jobId) throws IOException {
        if (!isValidJobId(jobId)) {
            sendJson(resp, HttpServletResponse.SC_BAD_REQUEST, false, "无效的任务 ID");
            return;
        }
        Optional<Integer> userIdOpt = requireUser(req);
        if (userIdOpt.isEmpty()) {
            sendJson(resp, HttpServletResponse.SC_UNAUTHORIZED, false, "请先登录");
            return;
        }

        Optional<VideoRenderJob> jobOpt = Main.getVideoRenderDatabaseManager()
                .findByIdAndUserId(jobId, userIdOpt.get());
        if (jobOpt.isEmpty()) {
            sendJson(resp, HttpServletResponse.SC_NOT_FOUND, false, "任务不存在");
            return;
        }
        VideoRenderJob job = jobOpt.get();

        JsonObject data = new JsonObject();
        data.addProperty("jobId", job.getId());
        data.addProperty("status", job.getStatus());
        data.addProperty("musicId", job.getMusicId());
        data.addProperty("durationSec", job.getDurationSec());
        data.addProperty("watermarked", job.isWatermarked());
        if (job.getErrorMessage() != null) {
            data.addProperty("error", job.getErrorMessage());
        }
        if ("done".equals(job.getStatus())) {
            data.addProperty("downloadUrl", "/api/video/render/" + job.getId() + "/download");
        }

        JsonObject out = new JsonObject();
        out.addProperty("success", true);
        out.add("data", data);
        sendRawJson(resp, HttpServletResponse.SC_OK, out);
    }

    private void handleDownload(HttpServletRequest req, HttpServletResponse resp, String jobId) throws IOException {
        if (!isValidJobId(jobId)) {
            sendJson(resp, HttpServletResponse.SC_BAD_REQUEST, false, "无效的任务 ID");
            return;
        }
        Optional<Integer> userIdOpt = requireUser(req);
        if (userIdOpt.isEmpty()) {
            sendJson(resp, HttpServletResponse.SC_UNAUTHORIZED, false, "请先登录");
            return;
        }

        Optional<VideoRenderJob> jobOpt = Main.getVideoRenderDatabaseManager()
                .findByIdAndUserId(jobId, userIdOpt.get());
        if (jobOpt.isEmpty()) {
            sendJson(resp, HttpServletResponse.SC_NOT_FOUND, false, "任务不存在");
            return;
        }
        VideoRenderJob job = jobOpt.get();
        if (!"done".equals(job.getStatus())) {
            sendJson(resp, HttpServletResponse.SC_CONFLICT, false, "视频尚未生成完成");
            return;
        }

        Path file = VideoRenderPaths.outputFile(job.getId());
        if (!Files.isRegularFile(file) || !VideoRenderPaths.isAllowedOutput(file)) {
            sendJson(resp, HttpServletResponse.SC_NOT_FOUND, false, "视频文件不存在");
            return;
        }

        String etag = HttpResourceCache.strongEtagForFile(file);
        if (HttpResourceCache.sendNotModifiedIfFresh(req, resp, etag)) {
            return;
        }

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("video/mp4");
        resp.setHeader("Content-Disposition", "attachment; filename=\"neko-clip-" + job.getMusicId() + ".mp4\"");
        HttpResourceCache.applyFileCachingHeaders(file, resp);
        resp.setContentLengthLong(Files.size(file));

        try (OutputStream os = resp.getOutputStream()) {
            Files.copy(file, os);
        }
    }

    private static Optional<Integer> requireUser(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        return Main.getUserAuthService().validateToken(auth == null ? "" : auth);
    }

    private static Optional<MusicMeta> loadMusicMeta(int musicId) {
        try (Connection conn = Main.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT title, artist, duration FROM music WHERE id = ? LIMIT 1")) {
            ps.setInt(1, musicId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    MusicMeta m = new MusicMeta();
                    m.title = rs.getString("title");
                    m.artist = rs.getString("artist");
                    m.durationSec = rs.getInt("duration");
                    return Optional.of(m);
                }
            }
        } catch (Exception e) {
            logger.error("读取音乐信息失败 musicId={}", musicId, e);
        }
        return Optional.empty();
    }

    private static boolean isCreatePath(HttpServletRequest req) {
        return "/create".equals(normalizePathInfo(req.getPathInfo()));
    }

    private static String normalizePathInfo(String pathInfo) {
        if (pathInfo == null || pathInfo.isEmpty()) {
            return "";
        }
        return pathInfo.endsWith("/") && pathInfo.length() > 1
                ? pathInfo.substring(0, pathInfo.length() - 1)
                : pathInfo;
    }

    private static boolean isValidJobId(String jobId) {
        return jobId != null && JOB_ID_PATTERN.matcher(jobId).matches();
    }

    private static String readBody(HttpServletRequest req) throws IOException {
        StringBuilder body = new StringBuilder();
        try (BufferedReader r = req.getReader()) {
            String line;
            while ((line = r.readLine()) != null) {
                body.append(line);
            }
        }
        return body.toString();
    }

    private static void sendJson(HttpServletResponse resp, int code, boolean success, String message) throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("success", success);
        o.addProperty("message", message);
        sendRawJson(resp, code, o);
    }

    private static void sendRawJson(HttpServletResponse resp, int code, JsonObject o) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setStatus(code);
        try (PrintWriter w = resp.getWriter()) {
            w.print(o.toString());
        }
    }

    private static final class MusicMeta {
        String title;
        String artist;
        int durationSec;
    }
}
