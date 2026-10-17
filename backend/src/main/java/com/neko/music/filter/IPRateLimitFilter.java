package com.neko.music.filter;

import com.neko.music.Main;
import com.neko.music.config.ConfigManager;
import com.neko.music.i18n.ApiMessages;
import com.neko.music.service.IPRateLimitService;
import com.neko.music.util.ClientIpResolver;
import com.neko.music.util.RequestPath;
import com.neko.music.util.RequestScope;
import com.neko.music.util.SsePaths;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * IP 频率限制过滤器（需在 {@link com.neko.music.Main} 中通过 {@code ServletContextHandler#addFilter} 注册；
 * 嵌入式 Jetty 不会处理 {@code @WebFilter}）。
 */
public class IPRateLimitFilter implements Filter {
    private static final Logger logger = LoggerFactory.getLogger(IPRateLimitFilter.class);

    private ConfigManager configManager;
    private IPRateLimitService ipRateLimitService;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // 从 ServletContext 获取 ConfigManager 和 IPRateLimitService
        ServletContext servletContext = filterConfig.getServletContext();
        configManager = (ConfigManager) servletContext.getAttribute("configManager");
        ipRateLimitService = (IPRateLimitService) servletContext.getAttribute("ipRateLimitService");

        if (configManager == null) {
            logger.error("ConfigManager 未找到，IP 频率限制功能将无法使用");
        }
        if (ipRateLimitService == null) {
            logger.error("IPRateLimitService 未找到，IP 频率限制功能将无法使用");
        }

        logger.info("IP 频率限制过滤器已初始化");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (configManager == null || ipRateLimitService == null) {
            // 配置未加载：两套门禁都不可用，直接放行
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        if (isExemptPath(requestPath(httpRequest))) {
            chain.doFilter(request, response);
            return;
        }

        String clientIP = getClientIP(httpRequest);

        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // IP 频率限制 + 认证失败来源封禁合并为一次 Redis 往返（见 IPRateLimitService#evaluate）。
        // 结果同时挂到请求作用域，SourceBanFilter 直接复用，不再单独查一次 Redis。
        IPRateLimitService.Decision decision = ipRateLimitService.evaluate(clientIP);
        RequestScope.put(GUARD_DECISION_KEY, decision);

        if (!decision.allowed()) {
            logger.warn("请求被门禁拦截: ip={} code={} path={}", clientIP, decision.code(),
                    requestPath(httpRequest));
            writeRateLimitedResponse(httpResponse, clientIP, decision.retryAfterSeconds());
            return;
        }

        // 允许请求继续
        chain.doFilter(request, response);
    }

    /** {@link RequestScope} 键：本次请求的门禁判定结果（供 {@link SourceBanFilter} 复用）。 */
    public static final String GUARD_DECISION_KEY = "ipGuardDecision";

    /** 读取本次请求已算好的门禁判定；尚未判定（限流被关闭 / 路径豁免）时为 {@code null}。 */
    static IPRateLimitService.Decision cachedDecision() {
        return RequestScope.get(GUARD_DECISION_KEY);
    }

    /**
     * 不参与 IP 限流的请求（既不检查封锁、也不计数）：
     * <ul>
     *   <li>大文件上传端点：一次上传会被拆成多个分片请求，按请求计数会误触发 429；</li>
     *   <li>ZPay 异步通知：由平台服务器回调，误伤会丢失支付通知；</li>
     *   <li>SSE 长连接（见 {@link SsePaths}）：EventSource 断线后会自动重连、又无法携带自定义请求头，
     *       一条长连接会被按请求计数当成高频流量，NAT / 移动网络等共享出口下正常用户也会被打成 429，
     *       连接再也建不起来。</li>
     * </ul>
     *
     * <p>传入的应为路由视角的规范化路径（见 {@link RequestPath}），豁免清单才不会因为
     * {@code /./api/x}、{@code /a/../api/x}、{@code /api;/x} 这类等价写法而失效。</p>
     */
    static boolean isExemptPath(String path) {
        return isMusicUploadPath(path) || isZpayNotifyPath(path) || SsePaths.isStreamPath(path);
    }

    /** 仅音乐上传接口不参与 IP 限流（不检查封锁、不计数），避免大文件上传分片误触发 429。 */
    private static boolean isMusicUploadPath(String path) {
        if (path.equals("/api/music/upload")) {
            return true;
        }
        return path.equals("/api/user/upload") || path.startsWith("/api/user/upload/");
    }

    /** ZPay 异步通知由平台服务器回调，不参与 IP 限流，避免通知失败。 */
    private static boolean isZpayNotifyPath(String path) {
        return path.equals("/api/payment/zpay/notify");
    }

    /** 请求路径：走 {@link RequestPath} 的「路由视角」规范化路径。 */
    private static String requestPath(HttpServletRequest req) {
        return RequestPath.of(req);
    }

    /**
     * 获取客户端真实 IP（统一走 {@link ClientIpResolver}：默认直连只取 socket 地址，
     * 杜绝客户端伪造转发头拆限流桶）。
     */
    private String getClientIP(HttpServletRequest request) {
        return ClientIpResolver.clientIp(request);
    }

    /**
     * 返回 429：{@code message} 内说明剩余封锁时长；{@code Retry-After} 为剩余秒数（HTTP 惯例）。
     *
     * @param retryAfterSeconds 判定时带回的剩余秒数；未知（{@code <= 0}）时回退到配置的封锁时长
     */
    private void writeRateLimitedResponse(HttpServletResponse httpResponse, String clientIP,
                                          long retryAfterSeconds) throws IOException {
        long remainingSec = retryAfterSeconds;
        if (remainingSec <= 0) {
            remainingSec = ipRateLimitService.getBlockTimeRemaining(clientIP);
        }
        if (remainingSec <= 0) {
            remainingSec = Math.max(1, configManager.getRateLimitBlockDuration());
        }
        httpResponse.setStatus(429);
        httpResponse.setHeader("Retry-After", String.valueOf(remainingSec));
        httpResponse.setContentType("application/json;charset=UTF-8");

        var body = Main.getObjectMapper().createObjectNode();
        body.put("success", false);
        body.put("message", formatRateLimitMessage(remainingSec));
        body.putNull("data");

        try {
            Main.getObjectMapper().writeValue(httpResponse.getWriter(), body);
        } catch (Exception e) {
            logger.debug("写入限流响应失败: {}", e.getMessage());
        }
    }

    /**
     * 将剩余秒数写进一句提示，便于直接展示给用户（{@link SourceBanFilter} 复用同一文案）。
     *
     * <p>只保留有意义的单位：{@code 60} 秒写成「1 分钟」而不是「1 分 0 秒」——后者既是冗余的
     * 零值，也让句子读起来别扭。因此每个量级都有「双单位」与「单单位」两条文案。</p>
     */
    static String formatRateLimitMessage(long sec) {
        if (sec < 1) {
            sec = 1;
        }
        if (sec >= 86400) {
            long d = sec / 86400;
            long h = (sec % 86400) / 3600;
            return h > 0
                    ? ApiMessages.translate("security.rate_limit.retry_days_hours", d, h)
                    : ApiMessages.translate("security.rate_limit.retry_days", d);
        }
        if (sec >= 3600) {
            long h = sec / 3600;
            long m = (sec % 3600) / 60;
            return m > 0
                    ? ApiMessages.translate("security.rate_limit.retry_hours_minutes", h, m)
                    : ApiMessages.translate("security.rate_limit.retry_hours", h);
        }
        if (sec >= 60) {
            long m = sec / 60;
            long s = sec % 60;
            return s > 0
                    ? ApiMessages.translate("security.rate_limit.retry_minutes_seconds", m, s)
                    : ApiMessages.translate("security.rate_limit.retry_minutes", m);
        }
        return ApiMessages.translate("security.rate_limit.retry_seconds", sec);
    }

    @Override
    public void destroy() {
        logger.info("IP 频率限制过滤器已销毁");
    }
}
