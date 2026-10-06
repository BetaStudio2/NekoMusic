package com.neko.music.filter;

import com.neko.music.Main;
import com.neko.music.config.ConfigManager;
import com.neko.music.seo.BrowserEvidence;
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
import java.util.List;
import java.util.Locale;

/**
 * 防爬 / 客户端区分拦截过滤器：保护 JSON 接口（{@code /api/*}）不被爬虫、扫描器与脚本刷取。
 *
 * <p>两段式判定：</p>
 * <ol>
 *   <li><b>已知黑名单</b>：UA 命中爬虫 / 无头浏览器 / 命令行工具 / 安全扫描器关键词 → 403。</li>
 *   <li><b>浏览器完整性区分</b>（{@code network.browser_integrity_enabled}，默认开）：
 *       非配置放行名单、非原生客户端的请求，必须「UA 结构像真浏览器」且带浏览器特征头，
 *       否则 403。用于拦截未知 / 小众网站爬虫——它们常用自定义 UA 或只伪造 {@code Mozilla/} 前缀，
 *       不含渲染引擎标记，也无法凑齐浏览器特征头。</li>
 * </ol>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>浏览器以真实 UA（含 {@code Mozilla/} 与内核标记）通过 fetch 访问 /api，并携带
 *       {@code Accept} / {@code Accept-Language} 等头，恒不误伤；原生客户端（Android / PC / 播放器）
 *       在 {@link UserAgentClassifier#isNativeClient} 中一并放行，不影响 App。</li>
 *   <li>爬虫 / AI 抓取器不应访问 JSON 接口——它们应走 SEO 页（{@code StaticPageSeoFilter}）。</li>
 *   <li>ZPay 异步通知由支付平台服务器回调（常用 curl 等 UA），与 IP 限流一致地豁免，避免支付通知断裂。</li>
 *   <li>可通过 {@code network.crawler_protection_enabled=false} 关闭全部拦截；
 *       或 {@code network.browser_integrity_enabled=false} 只保留已知黑名单。</li>
 *   <li>第三方客户端可通过 {@code network.allow_client_user_agents} 登记 UA 子串放行。</li>
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

        // 0) 空 UA：Qt 桌面端（NekoMusic PC）默认不发送 User-Agent，保守放行（交 IP 限流兜底），
        //    避免误伤；有 UA 的未知爬虫仍走下面的区分拦截。
        if (ua == null || ua.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        // 1) 明确为爬虫 / 无头 / 命令行工具 / 安全扫描器 → 转 SEO
        //    （isBotForApi 已内置放行原生客户端）
        if (UserAgentClassifier.isBotForApi(ua)) {
            divertToSeo(httpRequest, httpResponse, path, ua);
            return;
        }

        // 2) 浏览器完整性区分拦截：识别未知 / 小众爬虫（自定义 UA、残缺或仅伪造 Mozilla 前缀）
        if (configManager.isBrowserIntegrityEnabled()) {
            // 2a) 配置的额外放行名单（第三方客户端登记）
            if (isAllowlistedClient(ua)) {
                chain.doFilter(request, response);
                return;
            }
            // 2b) 内置原生客户端始终放行
            if (UserAgentClassifier.isNativeClient(ua)) {
                chain.doFilter(request, response);
                return;
            }
            // 2c) 其余必须是「UA 结构像真浏览器」且「带浏览器特征头」
            if (UserAgentClassifier.looksLikeRealBrowser(ua) && BrowserEvidence.hasFetchEvidence(httpRequest)) {
                chain.doFilter(request, response);
                return;
            }
            divertToSeo(httpRequest, httpResponse, path, ua);
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * 爬虫访问 {@code /api}：GET / HEAD 返回 302 转到对应 SEO 页面（让抓取器拿到可索引的
     * 服务端 HTML，而不是 SPA 或 JSON；也不再直接 403）；其它方法没有对应 SEO 页，仍 403。
     */
    private static void divertToSeo(HttpServletRequest request, HttpServletResponse response, String path, String ua)
            throws IOException {
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
            String target = seoPageForApiPath(path);
            logger.info("爬虫访问 API 转 SEO: path={} -> {} UA={} remote={}",
                    path, target, ua, request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_FOUND); // 302
            response.setHeader("Location", target);
            response.setHeader("Cache-Control", "private, no-store");
            return;
        }
        logger.warn("防爬拦截(非 GET): path={} method={} UA={} remote={}",
                path, method, ua, request.getRemoteAddr());
        writeForbidden(response);
    }

    /** {@code /api} 路径 → 对应 SEO 页面；无法对应时回首页。 */
    static String seoPageForApiPath(String path) {
        switch (path) {
            case "/api/music/ranking":
                return "/ranking";
            case "/api/music/latest":
                return "/latest";
            case "/api/music/search":
                return "/search";
            default:
                break;
        }
        for (String prefix : new String[]{
                "/api/music/info/", "/api/music/cover/", "/api/music/file/", "/api/music/lyrics/"}) {
            if (path.startsWith(prefix)) {
                String id = path.substring(prefix.length());
                int slash = id.indexOf('/');
                if (slash >= 0) {
                    id = id.substring(0, slash);
                }
                if (id.matches("\\d+")) {
                    return "/detail/" + id;
                }
            }
        }
        return "/";
    }

    /** 配置的额外放行 UA 子串匹配（大小写不敏感）。 */
    private boolean isAllowlistedClient(String ua) {
        if (ua == null || ua.isBlank()) {
            return false;
        }
        List<String> allowlist = configManager.getApiClientAllowlist();
        if (allowlist == null || allowlist.isEmpty()) {
            return false;
        }
        String lower = ua.toLowerCase(Locale.ROOT);
        for (String candidate : allowlist) {
            if (candidate != null && !candidate.isBlank()
                    && lower.contains(candidate.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
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