package com.neko.music.filter;

import com.neko.music.Main;
import com.neko.music.config.ConfigManager;
import com.neko.music.service.IPRateLimitService;
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

        if (configManager == null || ipRateLimitService == null || !configManager.isRateLimitEnabled()) {
            // 如果配置未加载或未启用频率限制，直接放行
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        if (isMusicUploadApiPath(httpRequest) || isZpayNotifyPath(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        String clientIP = getClientIP(httpRequest);

        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 检查 IP 是否被封锁
        if (ipRateLimitService.isIPBlocked(clientIP)) {
            logger.warn("封锁的 IP 尝试访问: {}", clientIP);
            writeRateLimitedResponse(httpResponse, clientIP);
            return;
        }

        // 记录请求并检查是否超过频率限制
        boolean allowed = ipRateLimitService.recordRequest(clientIP);
        if (!allowed) {
            logger.warn("IP {} 超过频率限制，请求被拦截", clientIP);
            writeRateLimitedResponse(httpResponse, clientIP);
            return;
        }

        // 允许请求继续
        chain.doFilter(request, response);
    }

    /**
     * 仅音乐上传接口不参与 IP 限流（不检查封锁、不计数），避免大文件上传误触发 429。
     */
    private static boolean isMusicUploadApiPath(HttpServletRequest req) {
        String path = req.getRequestURI();
        String ctx = req.getContextPath();
        if (ctx != null && !ctx.isEmpty() && path.startsWith(ctx)) {
            path = path.substring(ctx.length());
        }
        if (path.isEmpty()) {
            path = "/";
        } else if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.equals("/api/music/upload")) {
            return true;
        }
        return path.equals("/api/user/upload") || path.startsWith("/api/user/upload/");
    }

    /** ZPay 异步通知由平台服务器回调，不参与 IP 限流，避免通知失败。 */
    private static boolean isZpayNotifyPath(HttpServletRequest req) {
        String path = req.getRequestURI();
        String ctx = req.getContextPath();
        if (ctx != null && !ctx.isEmpty() && path.startsWith(ctx)) {
            path = path.substring(ctx.length());
        }
        if (path.isEmpty()) {
            path = "/";
        } else if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path.equals("/api/payment/zpay/notify");
    }

    /**
     * 获取客户端真实 IP
     */
    private String getClientIP(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // 如果有多个 IP（通过代理），取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip;
    }

    /**
     * 返回 429：{@code message} 内说明剩余封锁时长；{@code Retry-After} 为剩余秒数（HTTP 惯例）。
     */
    private void writeRateLimitedResponse(HttpServletResponse httpResponse, String clientIP) throws IOException {
        long remainingSec = ipRateLimitService.getBlockTimeRemaining(clientIP);
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

    /** 将剩余秒数写进一句中文提示，便于直接展示给用户。 */
    private static String formatRateLimitMessage(long sec) {
        if (sec < 1) {
            sec = 1;
        }
        if (sec >= 86400) {
            long d = sec / 86400;
            long h = (sec % 86400) / 3600;
            return String.format("请求过于频繁，请 %d 天 %d 小时后可重试", d, h);
        }
        if (sec >= 3600) {
            long h = sec / 3600;
            long m = (sec % 3600) / 60;
            return String.format("请求过于频繁，请 %d 小时 %d 分钟后可重试", h, m);
        }
        if (sec >= 60) {
            long m = sec / 60;
            long s = sec % 60;
            return String.format("请求过于频繁，请 %d 分 %d 秒后可重试", m, s);
        }
        return String.format("请求过于频繁，请 %d 秒后可重试", sec);
    }

    @Override
    public void destroy() {
        logger.info("IP 频率限制过滤器已销毁");
    }
}
