package com.neko.music.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.neko.music.Main;
import com.neko.music.database.ReplaySourceMarkDatabaseManager;
import com.neko.music.service.ReplaySourceMarkService;
import com.neko.music.util.AdminPermissionUtil;
import com.neko.music.util.PermissionHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * 后台查看 / 放行「防重放加压越界」标记的来源（{@code GET|DELETE /api/admin/replay-marks}）。
 *
 * <p>加压阶梯没有上限（见 {@code ReplayDifficultyPolicy}），越过可解线的来源会被落库标记并临时拒绝
 * 领取（见 {@link ReplaySourceMarkService}）。本接口只做两件事：把当前有效标记列出来、按来源清除标记。
 * 属于后台管理接口，**不写入 API 文档**。</p>
 */
public class AdminReplayMarkHandler extends ApiServlet {

    private static final Logger logger = LoggerFactory.getLogger(AdminReplayMarkHandler.class);

    /** 单次最多返回多少条标记。 */
    private static final int DEFAULT_LIMIT = 200;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!PermissionHelper.checkPermission(request, response,
                AdminPermissionUtil.Permission.SETTINGS_VIEW)) {
            logger.warn("权限不足，无法查看防重放来源标记");
            return;
        }

        int limit = parseLimit(request.getParameter("limit"));
        List<ReplaySourceMarkDatabaseManager.Mark> marks =
                Main.getReplaySourceMarkDatabaseManager() == null
                        ? List.of()
                        : Main.getReplaySourceMarkDatabaseManager().listActive(limit);

        JsonArray array = new JsonArray();
        for (ReplaySourceMarkDatabaseManager.Mark mark : marks) {
            JsonObject item = new JsonObject();
            item.addProperty("ip", mark.ip());
            item.addProperty("marks", mark.marks());
            item.addProperty("peakDifficultyBits", mark.peakDifficultyBits());
            item.addProperty("windowRequests", mark.windowRequests());
            item.addProperty("reason", mark.reason());
            item.add("firstMarkedAt", timestamp(mark.firstMarkedAt()));
            item.add("lastMarkedAt", timestamp(mark.lastMarkedAt()));
            item.add("expiresAt", timestamp(mark.expiresAt()));
            array.add(item);
        }

        JsonObject data = new JsonObject();
        data.add("marks", array);
        data.addProperty("total", array.size());

        JsonObject body = new JsonObject();
        body.addProperty("success", true);
        body.addProperty("message", "");
        body.add("data", data);
        sendSuccessResponse(response, body);
    }

    /** 人工放行：清除该来源的标记，标记有效期内不再拒绝其领取。 */
    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!PermissionHelper.checkPermission(request, response,
                AdminPermissionUtil.Permission.SETTINGS_EDIT)) {
            logger.warn("权限不足，无法清除防重放来源标记");
            return;
        }

        String ip = request.getParameter("ip");
        if (ip == null || ip.isBlank()) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "缺少 ip 参数");
            return;
        }

        ReplaySourceMarkService marks = ReplaySourceMarkService.current();
        boolean cleared = marks != null && marks.clear(ip.trim());

        JsonObject data = new JsonObject();
        data.addProperty("ip", ip.trim());
        data.addProperty("cleared", cleared);

        JsonObject body = new JsonObject();
        body.addProperty("success", true);
        body.addProperty("message", cleared ? "已清除标记" : "未找到有效标记");
        body.add("data", data);
        sendSuccessResponse(response, body);
    }

    /** 时间戳按 ISO 样式的字面量回传；缺值回 {@code null} 而不是字符串 "null"。 */
    private static com.google.gson.JsonElement timestamp(java.sql.Timestamp value) {
        return value == null
                ? com.google.gson.JsonNull.INSTANCE
                : new com.google.gson.JsonPrimitive(value.toLocalDateTime().toString());
    }

    private static int parseLimit(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_LIMIT;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return Math.max(1, Math.min(value, 500));
        } catch (NumberFormatException e) {
            return DEFAULT_LIMIT;
        }
    }
}
