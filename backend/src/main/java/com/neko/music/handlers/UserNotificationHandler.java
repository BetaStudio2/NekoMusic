package com.neko.music.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.neko.music.Main;
import com.neko.music.database.UserNotificationDatabaseManager;
import com.neko.music.database.UserNotificationDatabaseManager.NotificationRow;
import com.neko.music.database.UserNotificationDatabaseManager.Page;
import com.neko.music.service.UserNotificationService;
import com.neko.music.util.HttpResourceCache;
import com.neko.music.util.RequestAuthUtil;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 站内消息（收件箱）。
 *
 * <ul>
 *   <li>{@code GET  /api/user/notifications} — 列表（含未读数）；{@code since} 补拉离线期间的新消息，
 *       {@code before} 翻更早的历史，两者互斥（同时给时以 {@code since} 为准）</li>
 *   <li>{@code GET  /api/user/notifications/stream} — 实时推送（SSE 长连接，连上即带未读数）</li>
 *   <li>{@code POST /api/user/notifications/read} — 标记已读，body {@code {ids:[...]}}；
 *       {@code ids} 为空表示全部已读</li>
 * </ul>
 *
 * <p><b>离线兜底：</b>消息写库即视为送达，列表接口才是「上线后看到消息」的主路径；
 * {@code /stream} 只是在线时的提前告知，断开与重连都不影响消息完整性。</p>
 *
 * <p><b>SSE 契约：</b>连上先发一帧 {@code ready}（含未读数与当前最大 id），此后每有新消息推一帧
 * {@code message}（负载与列表里的单条消息一致），15 秒发一次注释心跳。连接最长存活
 * {@value #STREAM_MAX_LIFETIME_MS} 毫秒后主动断开、由客户端重连；重连期间漏掉的消息由客户端
 * 拿 {@code ready.latestId} 与本地游标比对后走列表接口补拉，服务端不做重放。</p>
 */
public class UserNotificationHandler extends ApiServlet {

    private static final Logger logger = LoggerFactory.getLogger(UserNotificationHandler.class);

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    /** 单次 SSE 连接最长存活时间：到点主动断开，避免长连接与游标状态无限累积。 */
    private static final long STREAM_MAX_LIFETIME_MS = 300_000L;
    /** 心跳间隔：防止反向代理 / CDN 掐掉长时间没有数据的连接。 */
    private static final long STREAM_PING_INTERVAL_MS = 15_000L;
    /** 实时信号轮询间隔：只读 Redis 里的版本号，够快又几乎不产生成本。 */
    private static final long STREAM_POLL_INTERVAL_MS = 1_000L;
    /** 单次补推的最大条数，避免突发批量把一条连接堵死。 */
    private static final int STREAM_BATCH_LIMIT = MAX_LIMIT;
    /** 每个用户同时允许的流连接数（多标签页 / 多端）。 */
    private static final int MAX_STREAMS_PER_USER = 2;
    /** 全局同时允许的流连接数，防止被大量连接拖垮。 */
    private static final int MAX_STREAMS_TOTAL = 1000;

    /**
     * 在册的流连接。{@code /stream} 因为浏览器 EventSource 无法携带自定义请求头而豁免了防重放，
     * 所以这里必须自己兜住连接数：按用户限并发 + 全局总量上限。
     */
    private static final ConcurrentHashMap<Integer, AtomicInteger> STREAMS_BY_USER = new ConcurrentHashMap<>();
    private static final AtomicInteger STREAM_TOTAL = new AtomicInteger();

    private final ExecutorService streamExecutor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("notification-sse-", 0).factory());

    @Override
    public void destroy() {
        streamExecutor.shutdownNow();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // 收件箱是逐用户的动态数据：显式声明不可缓存，避免任何中间层串味
        resp.setHeader("Cache-Control", HttpResourceCache.CACHE_CONTROL_NO_STORE);
        Integer userId = RequestAuthUtil.authenticate(req);
        if (userId == null) {
            sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "请先登录");
            return;
        }
        String path = path(req);
        if ("".equals(path) || "/".equals(path)) {
            handleList(req, resp, userId);
        } else if ("/stream".equals(path)) {
            handleStream(req, resp, userId);
        } else {
            sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "接口不存在");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setHeader("Cache-Control", HttpResourceCache.CACHE_CONTROL_NO_STORE);
        Integer userId = RequestAuthUtil.authenticate(req);
        if (userId == null) {
            sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "请先登录");
            return;
        }
        if ("/read".equals(path(req))) {
            handleMarkRead(req, resp, userId);
        } else {
            sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "接口不存在");
        }
    }

    // ──────────────────────────── 列表 / 未读 / 已读 ────────────────────────────

    /** 收件箱列表：新的在前；返回未读数与「是否还有更早的」，便于一次请求同时刷新红点。 */
    private void handleList(HttpServletRequest req, HttpServletResponse resp, int userId) throws IOException {
        Integer since = intParam(req, "since");
        Integer before = intParam(req, "before");
        int limit = clampLimit(intParam(req, "limit"));

        UserNotificationDatabaseManager db = Main.getUserNotificationDatabaseManager();
        Page page = db.list(userId, since, before, limit);

        JsonArray items = new JsonArray();
        int latestId = since == null ? 0 : since;
        for (NotificationRow row : page.rows) {
            items.add(toJson(row));
            latestId = Math.max(latestId, row.id);
        }

        JsonObject data = new JsonObject();
        data.add("items", items);
        data.addProperty("unread", db.countUnread(userId));
        data.addProperty("hasMore", page.hasMore);
        data.addProperty("latestId", latestId);

        JsonObject body = new JsonObject();
        body.addProperty("success", true);
        body.addProperty("message", "获取成功");
        body.add("data", data);
        sendSuccessResponse(resp, body);
    }

    private void handleMarkRead(HttpServletRequest req, HttpServletResponse resp, int userId) throws IOException {
        List<Integer> ids = readIds(req);

        UserNotificationDatabaseManager db = Main.getUserNotificationDatabaseManager();
        int updated = db.markRead(userId, ids);

        JsonObject data = new JsonObject();
        data.addProperty("updated", updated);
        data.addProperty("unread", db.countUnread(userId));

        JsonObject body = new JsonObject();
        body.addProperty("success", true);
        body.addProperty("message", "已标记为已读");
        body.add("data", data);
        sendSuccessResponse(resp, body);
    }

    /** 解析 {@code {ids:[...]}}；空 / 缺省 / 非法元素都按「全部已读」或跳过处理。 */
    private List<Integer> readIds(HttpServletRequest req) throws IOException {
        List<Integer> ids = new ArrayList<>();
        String raw = readBody(req);
        if (raw == null || raw.isBlank()) {
            return ids;
        }
        JsonObject request;
        try {
            request = Main.getGson().fromJson(raw, JsonObject.class);
        } catch (Exception e) {
            return ids;
        }
        if (request == null || !request.has("ids") || !request.get("ids").isJsonArray()) {
            return ids;
        }
        for (JsonElement element : request.getAsJsonArray("ids")) {
            if (element == null || !element.isJsonPrimitive()) {
                continue;
            }
            try {
                ids.add(element.getAsInt());
            } catch (NumberFormatException ignored) {
                // 非数字元素跳过，不因为坏参数整体失败
            }
        }
        return ids;
    }

    // ──────────────────────────── 实时推送（SSE） ────────────────────────────

    private void handleStream(HttpServletRequest req, HttpServletResponse resp, int userId) throws IOException {
        if (!acquireStreamSlot(userId)) {
            resp.setHeader("Retry-After", "30");
            sendErrorResponse(resp, 429, "消息连接数已达上限，请稍后再试");
            return;
        }

        AsyncContext asyncContext;
        try {
            asyncContext = req.startAsync();
        } catch (IllegalStateException e) {
            releaseStreamSlot(userId);
            logger.warn("站内消息 SSE 异步上下文创建失败", e);
            sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "服务不支持流式响应");
            return;
        }
        asyncContext.setTimeout(0);

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("text/event-stream;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        resp.setHeader("Connection", "keep-alive");
        resp.setHeader("X-Accel-Buffering", "no");

        PrintWriter writer;
        try {
            writer = resp.getWriter();
            // 先告诉浏览器重连间隔，避免它拿默认值猛打
            writer.print("retry: 3000\n\n");
            writer.flush();
        } catch (IOException | IllegalStateException e) {
            releaseStreamSlot(userId);
            completeQuietly(asyncContext);
            return;
        }

        streamExecutor.execute(() -> {
            try {
                streamNotifications(userId, writer);
            } finally {
                releaseStreamSlot(userId);
                completeQuietly(asyncContext);
            }
        });
    }

    /**
     * 连接存续期间只读 Redis 版本号，变了才去数据库取新消息。
     *
     * <p>消息可能由另一台实例写入（Redis 信号是共享的），所以这里不做本地事件监听。</p>
     */
    private void streamNotifications(int userId, PrintWriter writer) {
        UserNotificationDatabaseManager db = Main.getUserNotificationDatabaseManager();
        UserNotificationService notificationService = Main.getUserNotificationService();

        long deadline = System.currentTimeMillis() + STREAM_MAX_LIFETIME_MS;
        long lastPing = System.currentTimeMillis();
        long lastVersion = notificationService.currentVersion(userId);
        int cursor = latestId(db, userId);

        try {
            writeEvent(writer, "ready", readyPayload(db, userId, cursor));
            while (true) {
                long version = notificationService.currentVersion(userId);
                if (version != lastVersion) {
                    lastVersion = version;
                    cursor = pushNewItems(db, writer, userId, cursor);
                }

                long now = System.currentTimeMillis();
                if (now >= deadline) {
                    break;
                }
                if (now - lastPing >= STREAM_PING_INTERVAL_MS) {
                    writer.print(": ping\n\n");
                    writer.flush();
                    lastPing = now;
                }
                Thread.sleep(STREAM_POLL_INTERVAL_MS);
            }
        } catch (IOException e) {
            logger.debug("站内消息 SSE 连接已断开: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 把游标之后的新消息按 id 升序推完，返回推进后的游标。 */
    private int pushNewItems(UserNotificationDatabaseManager db, PrintWriter writer, int userId, int cursor)
            throws IOException {
        int latest = cursor;
        while (true) {
            Page page = db.list(userId, latest, null, STREAM_BATCH_LIMIT);
            if (page.rows.isEmpty()) {
                return latest;
            }
            List<NotificationRow> ascending = new ArrayList<>(page.rows);
            Collections.reverse(ascending);
            for (NotificationRow row : ascending) {
                writeEvent(writer, "message", toJson(row), row.id);
                latest = Math.max(latest, row.id);
            }
            if (!page.hasMore) {
                return latest;
            }
        }
    }

    /** 连接建立时的当前最大 id：客户端拿它和本地游标比对，落后就自己补拉。 */
    private static int latestId(UserNotificationDatabaseManager db, int userId) {
        Page page = db.list(userId, null, null, 1);
        return page.rows.isEmpty() ? 0 : page.rows.get(0).id;
    }

    private static JsonObject readyPayload(UserNotificationDatabaseManager db, int userId, int latestId) {
        JsonObject data = new JsonObject();
        data.addProperty("unread", db.countUnread(userId));
        data.addProperty("latestId", latestId);
        return data;
    }

    /**
     * 写一帧 SSE。{@code data} 必须是单行 JSON（Gson 的 toString 天然单行），
     * 否则折行会被客户端当成新的字段解析。
     */
    private static void writeEvent(PrintWriter writer, String event, JsonObject data) {
        writeEvent(writer, event, data, null);
    }

    private static void writeEvent(PrintWriter writer, String event, JsonObject data, Integer id) {
        if (id != null) {
            writer.print("id: " + id + "\n");
        }
        writer.print("event: " + event + "\n");
        writer.print("data: " + data + "\n\n");
        writer.flush();
    }

    private static synchronized boolean acquireStreamSlot(int userId) {
        if (STREAM_TOTAL.get() >= MAX_STREAMS_TOTAL) {
            return false;
        }
        AtomicInteger counter = STREAMS_BY_USER.computeIfAbsent(userId, key -> new AtomicInteger());
        if (counter.get() >= MAX_STREAMS_PER_USER) {
            return false;
        }
        counter.incrementAndGet();
        STREAM_TOTAL.incrementAndGet();
        return true;
    }

    private static synchronized void releaseStreamSlot(int userId) {
        AtomicInteger counter = STREAMS_BY_USER.get(userId);
        if (counter != null && counter.decrementAndGet() <= 0) {
            STREAMS_BY_USER.remove(userId);
        }
        STREAM_TOTAL.updateAndGet(value -> Math.max(0, value - 1));
    }

    private static void completeQuietly(AsyncContext asyncContext) {
        try {
            asyncContext.complete();
        } catch (IllegalStateException ignored) {
            // 连接已经被容器收回，无需处理
        }
    }

    // ──────────────────────────── 工具 ────────────────────────────

    /** 单条消息的对外结构；字段名即客户端契约，改动要同步 API 文档。 */
    static JsonObject toJson(NotificationRow row) {
        JsonObject json = new JsonObject();
        json.addProperty("id", row.id);
        json.addProperty("type", row.type);
        json.addProperty("title", row.title);
        json.addProperty("body", row.body);
        json.addProperty("link", row.link);
        json.addProperty("read", row.read);
        json.addProperty("createdAt", row.createdAt);
        if (row.actorUserId != null) {
            JsonObject actor = new JsonObject();
            actor.addProperty("id", row.actorUserId);
            actor.addProperty("nickname", row.actorNickname == null ? "" : row.actorNickname);
            json.add("actor", actor);
        }
        return json;
    }

    private static Integer intParam(HttpServletRequest req, String name) {
        String raw = req.getParameter(name);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int clampLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static String path(HttpServletRequest request) {
        String pathInfo = request.getPathInfo();
        return pathInfo == null ? "" : pathInfo;
    }
}
