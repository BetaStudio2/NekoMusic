package com.neko.music.handlers;

import com.neko.music.model.LogPage;
import com.neko.music.service.AccessLogService;
import com.neko.music.service.OperationLogService;
import com.neko.music.util.AdminPermissionUtil;
import com.neko.music.util.HttpResourceCache;
import com.neko.music.util.PermissionHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 后台日志查询接口（仅管理后台使用，不写入对外 API 文档）。
 *
 * <ul>
 *   <li>{@code GET /api/admin/logs/access} —— 分页检索访问日志</li>
 *   <li>{@code GET /api/admin/logs/access/stats} —— 访问量概览与聚合榜</li>
 *   <li>{@code GET /api/admin/logs/operations} —— 分页检索操作日志</li>
 * </ul>
 *
 * <p>权限：访问日志对「除普通用户外」的角色开放（审核员及以上）；操作日志仅管理员及以上。</p>
 */
public class AdminLogHandler extends ApiServlet {

    private static final Logger logger = LoggerFactory.getLogger(AdminLogHandler.class);

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;
    private static final int DEFAULT_STATS_DAYS = 7;
    private static final int MAX_STATS_DAYS = 90;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 动态接口显式声明不缓存（CacheControlFilter 也兜底，这里保持契约显式）
        response.setHeader("Cache-Control", HttpResourceCache.CACHE_CONTROL_NO_STORE);

        String path = request.getPathInfo();
        if (path == null) {
            path = "";
        }
        try {
            if (path.startsWith("/access/stats") || path.equals("/access/stats/")) {
                handleAccessStats(request, response);
            } else if (path.equals("/access") || path.equals("/access/")) {
                handleAccessList(request, response);
            } else if (path.equals("/operations") || path.equals("/operations/")) {
                handleOperationList(request, response);
            } else {
                sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, "未知的日志接口");
            }
        } catch (SQLException e) {
            logger.error("查询日志失败: {}", e.getMessage());
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "日志库查询失败");
        }
    }

    private void handleAccessStats(HttpServletRequest request, HttpServletResponse response) throws IOException, SQLException {
        if (!PermissionHelper.checkPermission(request, response,
                AdminPermissionUtil.Permission.ACCESS_LOG_VIEW)) {
            return;
        }
        AccessLogService service = AccessLogService.current();
        if (service == null) {
            sendErrorResponse(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "访问日志功能未启用");
            return;
        }
        int days = parseInt(request.getParameter("days"), DEFAULT_STATS_DAYS, 1, MAX_STATS_DAYS);
        sendJson(response, HttpServletResponse.SC_OK, true, "ok", service.stats(days));
    }

    private void handleAccessList(HttpServletRequest request, HttpServletResponse response) throws IOException, SQLException {
        if (!PermissionHelper.checkPermission(request, response,
                AdminPermissionUtil.Permission.ACCESS_LOG_VIEW)) {
            return;
        }
        AccessLogService service = AccessLogService.current();
        if (service == null) {
            sendErrorResponse(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "访问日志功能未启用");
            return;
        }
        LogPage page = service.list(
                parseInt(request.getParameter("page"), 1, 1, Integer.MAX_VALUE),
                parseInt(request.getParameter("size"), DEFAULT_PAGE_SIZE, 1, MAX_PAGE_SIZE),
                request.getParameter("ip"),
                request.getParameter("path"),
                request.getParameter("clientType"),
                parseInteger(request.getParameter("status")),
                parseLong(request.getParameter("from")),
                parseLong(request.getParameter("to")),
                request.getParameter("keyword"));
        sendJson(response, HttpServletResponse.SC_OK, true, "ok", toData(page));
    }

    private void handleOperationList(HttpServletRequest request, HttpServletResponse response) throws IOException, SQLException {
        if (!PermissionHelper.checkPermission(request, response,
                AdminPermissionUtil.Permission.OPERATION_LOG_VIEW)) {
            return;
        }
        OperationLogService service = OperationLogService.current();
        if (service == null) {
            sendErrorResponse(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "操作日志功能未启用");
            return;
        }
        LogPage page = service.list(
                parseInt(request.getParameter("page"), 1, 1, Integer.MAX_VALUE),
                parseInt(request.getParameter("size"), DEFAULT_PAGE_SIZE, 1, MAX_PAGE_SIZE),
                parseInteger(request.getParameter("adminId")),
                request.getParameter("module"),
                parseLong(request.getParameter("from")),
                parseLong(request.getParameter("to")),
                request.getParameter("keyword"));
        sendJson(response, HttpServletResponse.SC_OK, true, "ok", toData(page));
    }

    private static Map<String, Object> toData(LogPage page) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", page.total());
        data.put("page", page.page());
        data.put("size", page.size());
        data.put("items", page.items());
        return data;
    }

    private static int parseInt(String raw, int fallback, int min, int max) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return Math.min(Math.max(value, min), max);
        } catch (NumberFormatException e) {
            return fallback;
        }
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

    private static Long parseLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
