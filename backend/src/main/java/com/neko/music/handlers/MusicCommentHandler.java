package com.neko.music.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.neko.music.Main;
import com.neko.music.database.CommentDatabaseManager;
import com.neko.music.database.CommentDatabaseManager.CommentRow;
import com.neko.music.service.IpRegionService;
import com.neko.music.util.PermissionHelper;
import com.neko.music.util.PublicMusicLookup;
import com.neko.music.util.RequestAuthUtil;
import com.neko.music.util.SensitiveWordUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 歌曲评论：列表 / 发表 / 回复 / 删除共用一个端点 {@code /api/comments}。
 *
 * <ul>
 *   <li>{@code GET  /api/comments?musicId=&page=&pageSize=} 拉取楼层（含回复），无需登录</li>
 *   <li>{@code POST /api/comments} body {@code {musicId, content, parentId?}} 发表评论或回复，需登录</li>
 *   <li>{@code DELETE /api/comments?id=} 删除自己的评论（管理员令牌可删任意一条）；
 *       物理删除，删楼层会连带删除该楼层下的全部回复</li>
 * </ul>
 *
 * <p>评论只做两层：顶层楼层 + 楼层内回复，回复再回复仍归到同一楼层并用 {@code replyToUser} 标记 @ 对象，
 * 避免无限嵌套带来的分页与展示复杂度。每条评论都会快照写入发表时的 IP 归属地。
 */
public class MusicCommentHandler extends ApiServlet {

    private static final Logger logger = LoggerFactory.getLogger(MusicCommentHandler.class);

    private static final String COOLDOWN_KEY_PREFIX = "comment:cooldown:";
    private static final int MAX_CONTENT_LENGTH = 500;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    /** 同一用户两次发评论的最小间隔（秒） */
    private static final int POST_INTERVAL_SECONDS = 5;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer musicId = parseInteger(req.getParameter("musicId"));
        if (musicId == null) {
            sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "缺少音乐ID");
            return;
        }

        if (PublicMusicLookup.findById(musicId).isEmpty()) {
            sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "音乐不存在");
            return;
        }

        int page = Math.max(1, parseIntegerOrDefault(req.getParameter("page"), 1));
        int pageSize = clamp(parseIntegerOrDefault(req.getParameter("pageSize"), DEFAULT_PAGE_SIZE),
                1, MAX_PAGE_SIZE);

        Integer viewerId = RequestAuthUtil.authenticate(req);
        boolean admin = viewerId == null && PermissionHelper.isAdminAuthorized(req);

        CommentDatabaseManager db = Main.getCommentDatabaseManager();
        int total = db.countTopLevel(musicId);
        List<CommentRow> roots = db.listTopLevel(musicId, pageSize, (page - 1) * pageSize);

        List<Integer> rootIds = new ArrayList<>();
        for (CommentRow root : roots) {
            rootIds.add(root.id);
        }
        Map<Integer, List<CommentRow>> repliesByRoot = new LinkedHashMap<>();
        for (CommentRow reply : db.listReplies(musicId, rootIds)) {
            repliesByRoot.computeIfAbsent(reply.parentId, key -> new ArrayList<>()).add(reply);
        }

        JsonArray comments = new JsonArray();
        for (CommentRow root : roots) {
            JsonObject floor = toJson(root, viewerId, admin);
            List<CommentRow> replies = repliesByRoot.getOrDefault(root.id, List.of());
            floor.addProperty("replyCount", replies.size());
            JsonArray replyArr = new JsonArray();
            for (CommentRow reply : replies) {
                replyArr.add(toJson(reply, viewerId, admin));
            }
            floor.add("replies", replyArr);
            comments.add(floor);
        }

        JsonObject data = new JsonObject();
        data.addProperty("musicId", musicId);
        data.addProperty("page", page);
        data.addProperty("pageSize", pageSize);
        data.addProperty("total", total);
        data.addProperty("totalComments", db.countAll(musicId));
        data.addProperty("totalPages", total == 0 ? 0 : (total + pageSize - 1) / pageSize);
        data.addProperty("hasMore", (long) page * pageSize < total);
        data.add("comments", comments);

        JsonObject body = new JsonObject();
        body.addProperty("success", true);
        body.addProperty("message", "获取评论成功");
        body.add("data", data);
        sendSuccessResponse(resp, body);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer userId = RequestAuthUtil.authenticate(req);
        if (userId == null) {
            sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "请先登录后再发表评论");
            return;
        }

        JsonObject request;
        try {
            request = Main.getGson().fromJson(readBody(req), JsonObject.class);
        } catch (Exception e) {
            sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "请求格式错误");
            return;
        }
        if (request == null) {
            sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "请求体不能为空");
            return;
        }

        Integer musicId = request.has("musicId") && !request.get("musicId").isJsonNull()
                ? request.get("musicId").getAsInt() : null;
        if (musicId == null) {
            sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "缺少音乐ID");
            return;
        }

        String content = request.has("content") && !request.get("content").isJsonNull()
                ? request.get("content").getAsString().trim() : "";
        if (content.isEmpty()) {
            sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "评论内容不能为空");
            return;
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "评论内容不能超过 " + MAX_CONTENT_LENGTH + " 字");
            return;
        }
        if (SensitiveWordUtil.contains(content)) {
            sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "评论内容包含违规词汇，请修改后再试");
            return;
        }

        if (PublicMusicLookup.findById(musicId).isEmpty()) {
            sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "音乐不存在");
            return;
        }

        Integer parentId = request.has("parentId") && !request.get("parentId").isJsonNull()
                ? request.get("parentId").getAsInt() : null;

        CommentDatabaseManager db = Main.getCommentDatabaseManager();
        Integer rootId = null;
        Integer replyToUserId = null;
        if (parentId != null) {
            CommentRow parent = db.findById(parentId);
            if (parent == null || parent.musicId != musicId) {
                sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "要回复的评论不存在");
                return;
            }
            rootId = parent.parentId != null ? parent.parentId : parent.id;
            replyToUserId = parent.userId;
        }

        if (Main.getRedisService() != null
                && !Main.getRedisService().setIfAbsentWithExpiry(COOLDOWN_KEY_PREFIX + userId, "1", POST_INTERVAL_SECONDS)) {
            sendCooldownResponse(resp, POST_INTERVAL_SECONDS);
            return;
        }

        String ipRegion = Main.getIpRegionService().resolve(IpRegionService.clientIp(req));
        int newId = db.insert(musicId, userId, rootId, replyToUserId, content, ipRegion);
        if (newId <= 0) {
            if (Main.getRedisService() != null) {
                Main.getRedisService().del(COOLDOWN_KEY_PREFIX + userId);
            }
            sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "评论发表失败，请稍后再试");
            return;
        }

        JsonObject data = new JsonObject();
        data.addProperty("id", newId);
        data.addProperty("musicId", musicId);
        data.addProperty("parentId", rootId);
        data.addProperty("content", content);
        data.addProperty("ipRegion", ipRegion);
        data.addProperty("createdAt", com.neko.music.util.DbTimeUtil.nowShanghaiWallClock());
        data.addProperty("isReply", parentId != null);

        JsonObject body = new JsonObject();
        body.addProperty("success", true);
        body.addProperty("message", parentId == null ? "评论成功" : "回复成功");
        body.add("data", data);
        sendSuccessResponse(resp, body);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer userId = RequestAuthUtil.authenticate(req);
        boolean admin = false;
        if (userId == null) {
            admin = PermissionHelper.isAdminAuthorized(req);
            if (!admin) {
                sendErrorResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, "请先登录");
                return;
            }
        }

        Integer id = parseInteger(req.getParameter("id"));
        if (id == null) {
            sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "缺少评论ID");
            return;
        }

        CommentDatabaseManager db = Main.getCommentDatabaseManager();
        CommentRow row = db.findById(id);
        if (row == null) {
            sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND, "评论不存在");
            return;
        }
        if (!admin && row.userId != userId) {
            sendErrorResponse(resp, HttpServletResponse.SC_FORBIDDEN, "只能删除自己的评论");
            return;
        }
        int affected = db.delete(id, row.parentId, userId == null ? 0 : userId, admin);
        if (affected <= 0) {
            sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "删除失败，请稍后再试");
            return;
        }

        JsonObject data = new JsonObject();
        data.addProperty("id", id);
        data.addProperty("repliesDeleted", row.parentId == null ? Math.max(0, affected - 1) : 0);
        JsonObject body = new JsonObject();
        body.addProperty("success", true);
        body.addProperty("message", "删除成功");
        body.add("data", data);
        sendSuccessResponse(resp, body);
    }

    /** 单条评论（含作者、被回复者、可否删除）→ JSON。 */
    private static JsonObject toJson(CommentRow row, Integer viewerId, boolean admin) {
        JsonObject json = new JsonObject();
        json.addProperty("id", row.id);
        json.addProperty("musicId", row.musicId);
        json.addProperty("content", row.content);
        json.addProperty("createdAt", row.createdAt);
        json.addProperty("ipRegion", row.ipRegion == null ? "" : row.ipRegion);
        json.addProperty("canDelete", admin || (viewerId != null && viewerId == row.userId));

        JsonObject user = new JsonObject();
        user.addProperty("id", row.userId);
        user.addProperty("nickname", row.nickname == null ? "" : row.nickname);
        json.add("user", user);

        if (row.replyToUserId != null) {
            JsonObject replyTo = new JsonObject();
            replyTo.addProperty("id", row.replyToUserId);
            replyTo.addProperty("nickname", row.replyToNickname == null ? "" : row.replyToNickname);
            json.add("replyToUser", replyTo);
        }
        return json;
    }

    private static Integer parseInteger(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int parseIntegerOrDefault(String raw, int fallback) {
        Integer parsed = parseInteger(raw);
        return parsed == null ? fallback : parsed;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
