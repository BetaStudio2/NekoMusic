package com.neko.music.filter;

import com.neko.music.Main;
import com.neko.music.config.ConfigManager;
import com.neko.music.seo.UserAgentClassifier;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 保守防爬过滤器：拦截 UA 明确为爬虫 / 无头浏览器 / 命令行工具的请求访问 JSON 接口（{@code /api/*}）。
 *
 * <p>设计要点：
 * <ul>
 *   <li>浏览器以真实 UA（含 {@code Mozilla/}）通过 fetch 访问 /api，恒不命中；原生客户端
 *       （Android / PC / 播放器）在 {@link UserAgentClassifier#isBotForApi} 中一并放行，不影响 App。</li>
 *   <li>爬虫 / AI 抓取器不应访问 JSON 接口——它们应走 SEO 页（{@code StaticPageSeoFilter}）。
 *       命中一律返回 403，从而明显压减无效的 API 抓取请求。</li>
 *   <li>ZPay 异步通知由支付平台服务器回调（常用 curl 等 UA），与 IP 限流一致地豁免，避免支付通知断裂。</li>
 *   <li>可通过 {@code network.crawler_protection_enabled=false} 一键关闭。</li>
 * </ul>
 *
 * 需在 {@link com.neko.music.Main} 中显式注册（嵌入式 Jetty 不处理 {@code @WebFilter}）。
 */
public class CrawlerProtectionFilter implements Filter {
    private static final Logger logger = LoggerFactory.getLogger(CrawlerProtectionFilter.class);

    private ConfigManager configManager;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        ServletContext servletContext = filterConfig.getServletContext();
        configManager = (ConfigManager) servletContext.getAttribute("configManager");
        if (configManager == null) {
            logger.error("ConfigManager 未找到，防爬拦截不可用");
        }
        logger.info("保守防爬过滤器已初始化");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)
                || configManager == null
                || !configManager.isCrawlerProtectionEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        String path = normalizedPath(httpRequest.getRequestURI(), httpRequest.getContextPath());
        if (!isApiPath(path) || isZpayNotifyPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        String ua = httpRequest.getHeader("User-Agent");
        if (!UserAgentClassifier.isBotForApi(ua)) {
            chain.doFilter(request, response);
            return;
        }

        // 命中：明确为爬虫/无头/命令行工具的请求来访问 JSON 接口，直接 403。
        logger.warn("防爬拦截: path={} UA={} remote={}",
                path, ua, httpRequest.getRemoteAddr());
        writeForbidden(httpResponse);
    }

    private static String normalizedPath(String uri, String ctx) {
        String path = uri;
        if (ctx != null && !ctx.isEmpty() && path.startsWith(ctx)) {
            path = path.substring(ctx.length());
        }
        if (path.isEmpty()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private static boolean isApiPath(String path) {
        return path.startsWith("/api/");
    }

    /** ZPay 异步通知由平台服务器回调，不参与防爬，避免通知失败（与 IP 限流豁免一致）。 */
    private static boolean isZpayNotifyPath(String path) {
        return path.equals("/api/payment/zpay/notify");
    }

    private static void writeForbidden(HttpServletResponse httpResponse) throws IOException {
        httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
        httpResponse.setContentType("application/json;charset=UTF-8");
        httpResponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
        httpResponse.setHeader("Cache-Control", "private, no-store");
        var body = Main.getObjectMapper().createObjectNode();
        body.put("success", false);
        body.put("message", "请求已拒绝");
        body.putNull("data");
        try {
            Main.getObjectMapper().writeValue(httpResponse.getWriter(), body);
        } catch (Exception e) {
            logger.debug("写入防爬响应失败: {}", e.getMessage());
        }
    }

    @Override
    public void destroy() {
        logger.info("保守防爬过滤器已销毁");
    }
}