package com.neko.music.handlers;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.neko.music.Main;
import com.neko.music.model.Playlist;
import com.neko.music.service.ExternalImportService;
import com.neko.music.util.RequestAuthUtil;
import com.neko.music.util.SensitiveWordUtil;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 外部歌单导入（QQ / 网易云），SSE 推送进度，并把命中曲目加入用户指定的歌单。
 *
 * <ul>
 *   <li>{@code /loser/netease/pull?playlistId=|ids=&targetPlaylistId=}</li>
 *   <li>{@code /loser/qq/pull?disstid=&targetPlaylistId=}</li>
 * </ul>
 *
 * <p>均需要用户令牌。曲目入库后可用返回的 {@code musicId} 通过
 * {@code /api/music/file/{id}} 获取音频。</p>
 */
public class ExternalImportHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(ExternalImportHandler.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handle(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handle(request, response);
    }

    private void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer userId = RequestAuthUtil.authenticate(request);
        if (userId == null) {
            writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "缺少或无效的用户令牌");
            return;
        }

        boolean qq = request.getServletPath() != null && request.getServletPath().endsWith("/qq/pull");

        String targetPlaylistIdParam = request.getParameter("targetPlaylistId");
        Integer targetPlaylistId = parseIntParam(targetPlaylistIdParam);
        if (targetPlaylistId == null && targetPlaylistIdParam != null && !targetPlaylistIdParam.isBlank()) {
            writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "targetPlaylistId 必须为数字");
            return;
        }

        int resolvedPlaylistId;
        boolean playlistCreated = false;
        if (targetPlaylistId != null) {
            if (!Main.getPlaylistService().isPlaylistOwner(targetPlaylistId, userId)) {
                writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "无权限修改此歌单");
                return;
            }
            resolvedPlaylistId = targetPlaylistId;
        } else {
            String targetPlaylistName = request.getParameter("targetPlaylistName");
            targetPlaylistName = targetPlaylistName == null ? null : targetPlaylistName.trim();
            if (targetPlaylistName == null || targetPlaylistName.isEmpty()) {
                writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST,
                        "需要提供 targetPlaylistId（导入到已有歌单）或 targetPlaylistName（新建歌单）");
                return;
            }
            if (targetPlaylistName.length() > 255) {
                writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "歌单名称过长");
                return;
            }
            if (SensitiveWordUtil.contains(targetPlaylistName)) {
                writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "歌单名称包含违禁词");
                return;
            }
            Optional<Playlist> created = Main.getPlaylistService()
                    .createPlaylist(userId, targetPlaylistName, "由外部歌单导入创建");
            if (created.isEmpty()) {
                writeJsonError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "创建歌单失败");
                return;
            }
            resolvedPlaylistId = created.get().getId();
            playlistCreated = true;
        }

        String disstid = null;
        Long neteasePlaylistId = null;
        List<Long> songIds = List.of();
        if (qq) {
            disstid = request.getParameter("disstid");
            if (disstid == null || !disstid.matches("[0-9]{1,19}")) {
                writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST,
                        "缺少有效的 disstid（QQ 歌单 ID 必须为数字）");
                return;
            }
        } else {
            String playlistParam = request.getParameter("playlistId");
            neteasePlaylistId = parseLongParam(playlistParam);
            if (neteasePlaylistId == null && playlistParam != null && !playlistParam.isBlank()) {
                writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "playlistId 必须为数字");
                return;
            }
            try {
                songIds = parseIds(request.getParameter("ids"));
            } catch (IllegalArgumentException e) {
                writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
                return;
            }
            if (neteasePlaylistId == null && songIds.isEmpty()) {
                writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "需要提供 playlistId 或 ids");
                return;
            }
        }

        ExternalImportService importService = Main.getExternalImportService();

        AsyncContext asyncContext;
        try {
            asyncContext = request.startAsync();
        } catch (IllegalStateException e) {
            logger.warn("SSE 异步上下文创建失败", e);
            writeJsonError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "服务不支持流式响应");
            return;
        }
        asyncContext.setTimeout(0);

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/event-stream;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");

        PrintWriter writer;
        try {
            writer = response.getWriter();
            writer.print(": connected\n\n");
            writer.flush();
        } catch (IOException | IllegalStateException e) {
            logger.warn("SSE 响应初始化失败: {}", e.getMessage());
            completeQuietly(asyncContext);
            return;
        }

        ExternalImportService.Listener listener = new SseListener(asyncContext, writer);
        if (qq) {
            importService.startQqImport(disstid, resolvedPlaylistId, playlistCreated, listener);
        } else {
            importService.startNeteaseImport(neteasePlaylistId, songIds, resolvedPlaylistId, playlistCreated,
                    listener);
        }
    }

    private static void completeQuietly(AsyncContext asyncContext) {
        try {
            asyncContext.complete();
        } catch (IllegalStateException ignored) {
            // 已结束
        }
    }

    private static Integer parseIntParam(String raw) {
        if (raw == null || !raw.matches("[0-9]{1,20}")) {
            return null;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long parseLongParam(String raw) {
        if (raw == null || !raw.matches("[0-9]{1,20}")) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<Long> parseIds(String raw) {
        List<Long> ids = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return ids;
        }
        String[] parts = raw.split(",");
        for (String part : parts) {
            String value = part.trim();
            if (value.isEmpty()) {
                continue;
            }
            if (!value.matches("[0-9]{1,20}")) {
                throw new IllegalArgumentException("ids 必须是逗号分隔的数字");
            }
            ids.add(Long.parseLong(value));
        }
        return ids;
    }

    private static void writeJsonError(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(Main.getObjectMapper().createObjectNode()
                .put("code", status).put("msg", message).toString());
    }

    /** 把导入服务的事件翻译为 SSE 帧。 */
    private static final class SseListener implements ExternalImportService.Listener {
        private final AsyncContext asyncContext;
        private final PrintWriter writer;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        SseListener(AsyncContext asyncContext, PrintWriter writer) {
            this.asyncContext = asyncContext;
            this.writer = writer;
        }

        @Override
        public void onStart(String source, int total, int targetPlaylistId, boolean targetPlaylistCreated) {
            ObjectNode data = Main.getObjectMapper().createObjectNode();
            data.put("source", source);
            data.put("total", total);
            data.put("targetPlaylistId", targetPlaylistId);
            data.put("targetPlaylistCreated", targetPlaylistCreated);
            send("start", data);
        }

        @Override
        public void onTrackStarted(ExternalImportService.TrackResult result) {
            send("track", trackNode(result));
        }

        @Override
        public void onTrackProgress(int index, int total, String sourceId, long bytesRead, long totalBytes) {
            ObjectNode data = Main.getObjectMapper().createObjectNode();
            data.put("index", index);
            data.put("total", total);
            data.put("sourceId", sourceId);
            data.put("bytes", bytesRead);
            data.put("totalBytes", totalBytes);
            data.put("percent", totalBytes > 0 ? Math.min(100, bytesRead * 100 / totalBytes) : -1);
            send("progress", data);
        }

        @Override
        public void onTrackFinished(ExternalImportService.TrackResult result) {
            send("track", trackNode(result));
        }

        @Override
        public void onComplete(ExternalImportService.Summary summary) {
            ObjectNode data = Main.getObjectMapper().createObjectNode();
            data.put("total", summary.total());
            data.put("imported", summary.imported());
            data.put("existed", summary.existed());
            data.put("failed", summary.failed());
            send("done", data);
            finish();
        }

        @Override
        public void onError(String message) {
            ObjectNode data = Main.getObjectMapper().createObjectNode();
            data.put("message", message == null ? "导入失败" : message);
            send("error", data);
            finish();
        }

        private ObjectNode trackNode(ExternalImportService.TrackResult result) {
            ObjectNode data = Main.getObjectMapper().createObjectNode();
            data.put("index", result.index());
            data.put("total", result.total());
            data.put("source", result.source());
            data.put("sourceId", result.sourceId());
            data.put("title", result.title() == null ? "" : result.title());
            data.put("artist", result.artist() == null ? "" : result.artist());
            data.put("status", result.status());
            data.put("playlistAdded", result.playlistAdded());
            if (result.musicId() != null) {
                data.put("musicId", result.musicId());
                data.put("fileUrl", "/api/music/file/" + result.musicId());
            } else {
                data.putNull("musicId");
                data.putNull("fileUrl");
            }
            if (result.message() == null) {
                data.putNull("message");
            } else {
                data.put("message", result.message());
            }
            return data;
        }

        private void send(String event, ObjectNode data) {
            if (closed.get()) {
                return;
            }
            String json;
            try {
                json = Main.getObjectMapper().writeValueAsString(data);
            } catch (IOException e) {
                closed.set(true);
                throw new UncheckedIOException("导入事件序列化失败", e);
            }
            writer.print("event: " + event + "\n");
            writer.print("data: " + json + "\n\n");
            writer.flush();
            if (writer.checkError()) {
                closed.set(true);
                throw new UncheckedIOException("SSE 连接已断开", new IOException("client disconnected"));
            }
        }

        private void finish() {
            closed.set(true);
            try {
                writer.flush();
            } catch (RuntimeException ignored) {
                // 连接已断开，忽略
            }
            try {
                asyncContext.complete();
            } catch (IllegalStateException ignored) {
                // 已结束
            }
        }
    }
}
