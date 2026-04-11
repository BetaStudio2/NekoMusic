package com.neko.music.filter;

import com.neko.music.config.ConfigManager;
import com.neko.music.service.IPRateLimitService;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * IP频率限制过滤器
 * 用于防止暴力攻击和滥用
 */
@WebFilter(urlPatterns = "/*", filterName = "IPRateLimitFilter")
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
        String clientIP = getClientIP(httpRequest);

        // 检查 IP 是否被封锁
        if (ipRateLimitService.isIPBlocked(clientIP)) {
            logger.warn("封锁的 IP 尝试访问: {}", clientIP);

            if (configManager.isRateLimitSilentTimeout()) {
                // 静默超时：什么都不返回，让对方自己 timeout
                logger.debug("对封锁的 IP {} 执行静默超时", clientIP);
                try {
                    // 保持连接打开但不发送任何响应，让对方自己 timeout
                    Thread.sleep(30000); // 等待 30 秒
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return;
            } else {
                // 返回错误信息
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"success\":false,\"message\":\"请求过于频繁，请稍后再试\",\"data\":null}");
                return;
            }
        }

        // 记录请求并检查是否超过频率限制
        boolean allowed = ipRateLimitService.recordRequest(clientIP);
        if (!allowed) {
            logger.warn("IP {} 超过频率限制，请求被拦截", clientIP);

            if (configManager.isRateLimitSilentTimeout()) {
                // 静默超时
                logger.debug("对超频 IP {} 执行静默超时", clientIP);
                try {
                    Thread.sleep(30000); // 等待 30 秒
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return;
            } else {
                // 返回错误信息
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"success\":false,\"message\":\"请求过于频繁，请稍后再试\",\"data\":null}");
                return;
            }
        }

        // 允许请求继续
        chain.doFilter(request, response);
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

    @Override
    public void destroy() {
        logger.info("IP 频率限制过滤器已销毁");
    }
}
