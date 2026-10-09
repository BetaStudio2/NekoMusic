package com.neko.music.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.neko.music.Main;
import com.neko.music.database.UserNotificationDatabaseManager;
import com.neko.music.database.UserNotificationDatabaseManager.NotificationRow;
import com.neko.music.database.UserNotificationDatabaseManager.Page;
import com.neko.music.util.HttpResourceCache;
import com.neko.music.util.RequestAuthUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 站内消息（收件箱）。
 *
 * <ul>
 *   <li>{@code GET  /api/user/notifications} — 列表；{@code since} 补拉离线期间的新消息，
 *       {@code before} 翻更早的历史，两者互斥（同时给时以 {@code since} 为准）</li>
 *   <li>{@code GET  /api/user/notifications/unread} — 未读条数</li>
 *   <li>{@code POST /api/user/notifications/read} — 标记已读，body {@code {ids:[...]}}；
 *       {@code ids} 为空表示全部已读</li>
 * </ul>
 *
 * <p>消息在写入库时即视为送达，这里的列表接口就是「离线用户上线后看到消息」的兜底路径，
 * 不依赖任何长连接。</p>
 */
public class UserNotificationHandler extends ApiServlet {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

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
        } else if ("/unread".equals(path)) {
            handleUnread(resp, userId);
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

    private void handleUnread(HttpServletResponse resp, int userId) throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("unread", Main.getUserNotificationDatabaseManager().countUnread(userId));

        JsonObject body = new JsonObject();
        body.addProperty("success", true);
        body.addProperty("message", "获取成功");
        body.add("data", data);
        sendSuccessResponse(resp, body);
    }

    private void handleMarkRead(HttpServletRequest req, HttpServletResponse resp, int userId) throws IOException {
        List<Integer> ids = new ArrayList<>();
        String raw = readBody(req);
        if (raw != null && !raw.isBlank()) {
            JsonObject request;
            try {
                request = Main.getGson().fromJson(raw, JsonObject.class);
            } catch (Exception e) {
                sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "请求格式错误");
                return;
            }
            if (request != null && request.has("ids") && request.get("ids").isJsonArray()) {
                for (JsonElement element : request.getAsJsonArray("ids")) {
                    if (element != null && element.isJsonPrimitive()) {
                        try {
                            ids.add(element.getAsInt());
                        } catch (NumberFormatException ignored) {
                            // 非数字元素直接跳过，不因为坏参数整体失败
                        }
                    }
                }
            }
        }

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
