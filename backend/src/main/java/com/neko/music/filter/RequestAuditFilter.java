package com.neko.music.filter;

import com.neko.music.Main;
import com.neko.music.config.ConfigManager;
import com.neko.music.model.Admin;
import com.neko.music.model.AccessLogRecord;
import com.neko.music.model.OperationLogRecord;
import com.neko.music.service.AccessLogService;
import com.neko.music.service.OperationLogService;
import com.neko.music.util.ClientFingerprint;
import com.neko.music.util.ClientIpResolver;
import com.neko.music.util.RequestCategory;
import com.neko.music.util.RequestPath;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * 访问日志 + 操作日志过滤器。
 *
 * <p>必须注册在过滤器链**最外层**（{@code Main} 里第一个 {@code addFilter}）：这样它能观察到
 * 经过后面所有门禁（限流 / 封禁 / 防爬 / 防重放）之后真实的响应状态码与总耗时——被 403、409、429
 * 挡下的请求同样要留痕，否则「谁在试探」这部分恰好缺失。</p>
 *
 * <ul>
 *   <li><b>访问日志</b>：每个请求一条，落 `access_log`（`logging.access_enabled` 控制）。</li>
 *   <li><b>操作日志</b>：`/api/admin/*` 上的写操作（POST/PUT/PATCH/DELETE）各一条，落
 *       `operation_log`（`logging.operation_enabled` 控制）。</li>
 * </ul>
 *
 * <p>两者都只做一次入队（异步批量落库），不参与请求关键路径；日志库不可用时为 no-op。</p>
 */
public class RequestAuditFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(RequestAuditFilter.class);

    /** 产生「操作日志」的方法：只有会改变服务端状态的方法才算操作。 */
    private static final List<String> MUTATING_METHODS = List.of("POST", "PUT", "PATCH", "DELETE");

    private static final String BEARER_PREFIX = "Bearer ";

    /** 管理后台路径 → 业务模块；按最长前缀匹配，命中顺序与列表顺序无关（先排序后匹配）。 */
    private static final List<String[]> ADMIN_MODULES = List.of(
            new String[] { "/api/admin/lyrics-files", "歌词管理" },
            new String[] { "/api/admin/replay-marks", "防重放标记" },
            new String[] { "/api/admin/settings", "系统设置" },
            new String[] { "/api/admin/releases", "客户端更新" },
            new String[] { "/api/admin/netease", "网易云补全" },
            new String[] { "/api/admin/vip/pricing", "VIP 定价" },
            new String[] { "/api/admin/users", "用户管理" },
            new String[] { "/api/admin/music", "音乐管理" },
            new String[] { "/api/admin/audit", "审核管理" },
            new String[] { "/api/admin/create", "管理员管理" },
            new String[] { "/api/admin/login", "管理员登录" },
            new String[] { "/api/admin/logout", "管理员登录" },
            new String[] { "/api/admin/current", "管理员会话" },
            new String[] { "/api/admin/stats", "数据统计" },
            new String[] { "/api/admin/chart-data", "数据统计" });

    private ConfigManager configManager;

    @Override
    public void init(FilterConfig filterConfig) {
        this.configManager = (ConfigManager) filterConfig.getServletContext().getAttribute("configManager");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)
                || httpRequest.getDispatcherType() != DispatcherType.REQUEST) {
            chain.doFilter(request, response);
            return;
        }

        long startedAt = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            try {
                capture(httpRequest, httpResponse, startedAt, System.currentTimeMillis());
            } catch (Exception e) {
                // 日志失败绝不能影响响应已经写出的内容
                logger.debug("记录请求日志失败: {}", e.getMessage());
            }
        }
    }

    private void capture(HttpServletRequest request, HttpServletResponse response,
                         long startedAt, long finishedAt) {
        boolean accessEnabled = configManager == null || configManager.isLoggingAccessEnabled();
        boolean operationEnabled = configManager == null || configManager.isLoggingOperationEnabled();
        if (!accessEnabled && !operationEnabled) {
            return;
        }

        String path = RequestPath.of(request);
        String category = RequestCategory.of(path);
        int status = response.getStatus();
        int durationMs = (int) Math.min(Integer.MAX_VALUE, finishedAt - startedAt);
        String authorization = request.getHeader("Authorization");
        boolean authenticated = authorization != null && authorization.startsWith(BEARER_PREFIX);

        boolean isAdminPath = RequestCategory.ADMIN.equals(category);
        String method = request.getMethod();
        boolean mutating = MUTATING_METHODS.contains(method.toUpperCase(java.util.Locale.ROOT));
        boolean logOperation = operationEnabled && isAdminPath && mutating;

        Admin admin = (isAdminPath && (logOperation || accessEnabled)) ? resolveAdmin(authorization) : null;

        if (accessEnabled) {
            AccessLogService.record(buildAccessRecord(request, response, path, category, method, status,
                    startedAt, durationMs, authenticated, admin));
        }
        if (logOperation) {
            OperationLogService.record(buildOperationRecord(request, path, method, status, startedAt,
                    durationMs, admin, resolveClientIp(request)));
        }
    }

    private AccessLogRecord buildAccessRecord(HttpServletRequest request, HttpServletResponse response,
                                              String path, String category, String method, int status,
                                              long occurredAt, int durationMs,
                                              boolean authenticated, Admin admin) {
        String userAgent = request.getHeader("User-Agent");
        String nekoClient = request.getHeader("X-Neko-Client");
        String clientIp = resolveClientIp(request);
        return new AccessLogRecord(
                occurredAt,
                method,
                path,
                request.getQueryString(),
                category,
                status,
                durationMs,
                responseBytes(response),
                clientIp,
                ClientFingerprint.ipSubnet(clientIp),
                userAgent,
                ClientFingerprint.type(userAgent),
                ClientFingerprint.platform(userAgent, nekoClient),
                ClientFingerprint.version(userAgent, nekoClient),
                nekoClient,
                request.getHeader("Referer"),
                request.getHeader("Origin"),
                request.getHeader("Accept-Language"),
                authenticated,
                admin == null ? null : admin.id(),
                admin == null ? null : admin.username(),
                request.getHeader("Host"),
                request.getScheme(),
                request.getProtocol(),
                Thread.currentThread().getName());
    }

    private OperationLogRecord buildOperationRecord(HttpServletRequest request, String path, String method,
                                                    int status, long occurredAt, int durationMs,
                                                    Admin admin, String clientIp) {
        String userAgent = request.getHeader("User-Agent");
        return new OperationLogRecord(
                occurredAt,
                admin == null ? null : admin.id(),
                admin == null ? null : admin.username(),
                admin == null ? null : admin.role(),
                resolveModule(path),
                resolveAction(path, method),
                method,
                path,
                request.getQueryString(),
                status,
                status >= 200 && status < 400,
                durationMs,
                clientIp,
                userAgent,
                ClientFingerprint.platform(userAgent, request.getHeader("X-Neko-Client")),
                null);
    }

    /** 管理后台路径 → 业务模块（最长前缀优先）。 */
    static String resolveModule(String path) {
        String[] best = null;
        for (String[] entry : ADMIN_MODULES) {
            if (path.startsWith(entry[0]) && (best == null || entry[0].length() > best[0].length())) {
                best = entry;
            }
        }
        return best == null ? "管理后台" : best[1];
    }

    /** 动作：登录 / 登出单列，其余按 HTTP 方法归纳。 */
    static String resolveAction(String path, String method) {
        if ("/api/admin/login".equals(path)) {
            return "登录";
        }
        if ("/api/admin/logout".equals(path)) {
            return "登出";
        }
        return switch (method.toUpperCase(java.util.Locale.ROOT)) {
            case "POST" -> "提交";
            case "PUT" -> "更新";
            case "PATCH" -> "更新";
            case "DELETE" -> "删除";
            default -> method;
        };
    }

    private static Admin resolveAdmin(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        try {
            if (Main.getAdminAuthService() == null) {
                return null;
            }
            return Main.getAdminAuthService().getAdminByToken(authorization.substring(BEARER_PREFIX.length()));
        } catch (Exception e) {
            return null;
        }
    }

    private static String resolveClientIp(HttpServletRequest request) {
        try {
            return ClientIpResolver.clientIp(request);
        } catch (Exception e) {
            return "";
        }
    }

    /** 响应体大小：优先取 Content-Length，取不到（chunked / 流式）记 0，不去包装响应流（会破坏 SSE 与媒体流）。 */
    private static long responseBytes(HttpServletResponse response) {
        String header = response.getHeader("Content-Length");
        if (header == null || header.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
