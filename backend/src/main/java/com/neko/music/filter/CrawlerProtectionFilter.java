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
 * 防爬 / 客户端区分拦截过滤器：保护动态接口（{@code /api/*}、{@code /loser/*}，后者为外部平台
 * 代理与歌单导入）不被爬虫、扫描器与脚本刷取。
 *
 * <p>两段式判定：</p>
 * <ol>
 *   <li><b>已知黑名单</b>：UA 命中爬虫 / 无头浏览器 / 命令行工具 / 安全扫描器关键词 → 直接 403。</li>
 *   <li><b>浏览器完整性区分</b>（{@code network.browser_integrity_enabled}，默认开）：
 *       非配置放行名单、非原生客户端的请求，必须「UA 结构像真浏览器」且带浏览器特征头，
 *       否则直接 403。用于拦截未知 / 小众网站爬虫——它们常用自定义 UA 或只伪造 {@code Mozilla/} 前缀，
 *       不含渲染引擎标记，也无法凑齐浏览器特征头。</li>
 * </ol>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>浏览器以真实 UA（含 {@code Mozilla/} 与内核标记）通过 fetch 访问动态接口，并携带
 *       {@code Accept} / {@code Accept-Language} 等头，恒不误伤；官方原生客户端靠
 *       {@link UserAgentClassifier#isNativeClient} 的严格 UA 判定放行，不影响 App。</li>
 *   <li>爬虫 / AI 抓取器 / 扫描器 / 伪造客户端不得拿到 JSON，也不在动态接口路径下渲染 SEO 页：
 *       命中即 {@code 403}。可抓取内容一律以正式 SEO 页面路径提供（sitemap + canonical），
 *       {@code robots.txt} 也已声明不与接口混在一起；在接口路径上再渲染一份 HTML 只会浪费渲染开销，
 *       并让同一 URL 对爬虫与浏览器出现两种表现。</li>
 *   <li>SEO 页（{@code og:image} / JSON-LD）引用的是公开静态媒体（{@code /media/*}），
 *       不指向动态接口，因此链接预览与图片收录不受本过滤器影响。</li>
 *   <li>{@code /api/music/ranking}、{@code /api/music/latest} 是公开且允许 CDN 缓存的接口
 *       （半小时）：对所有人返回同一份 JSON，不参与防爬判定。否则边缘缓存命中与否会让同一个
 *       UA 时而被拦、时而拿到 JSON，且缓存里落的是哪一版就发给所有人。</li>
 *   <li>ZPay 异步通知由支付平台服务器回调（常用 curl 等 UA），与 IP 限流一致地豁免，避免支付通知断裂。</li>
 *   <li>可通过 {@code network.crawler_protection_enabled=false} 关闭全部拦截；
 *       或 {@code network.browser_integrity_enabled=false} 只保留已知黑名单。</li>
 *   <li>第三方客户端可通过 {@code network.allow_client_user_agents} 登记 UA 子串放行；登记优先于
 *       黑名单判定，因此登记一个自带 {@code okhttp} 之类关键词的 UA 也能生效。</li>
 * </ul>
 *
 * 需在 {@link com.neko.music.Main} 中显式注册（嵌入式 Jetty 不处理 {@code @WebFilter}）。
 */
public class CrawlerProtectionFilter implements Filter {
    private static final Logger logger = LoggerFactory.getLogger(CrawlerProtectionFilter.class);

    /** 第三方客户端放行登记的最短长度：与 ConfigManager 的登记过滤保持一致。 */
    static final int MIN_ALLOWLIST_LENGTH = 6;

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
        if (!isGuardedPath(path) || isZpayNotifyPath(path) || isPublicCacheableApiPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        String ua = httpRequest.getHeader("User-Agent");

        // 0) 空 UA：一律按爬虫 / 脚本处理，直接 403。
        //    三端客户端都会显式携带 User-Agent（浏览器 / Android NekoMusic-android / PC NekoMusic-PC），
        //    媒体直链走 /media/* 不经本过滤器，因此不再为「不发 UA 的客户端」保留豁免：
        //    否则任何脚本只要不带 UA 就能绕过防爬与浏览器完整性两层校验。
        if (ua == null || ua.isBlank()) {
            writeForbidden(httpResponse);
            return;
        }

        // 1) 配置里登记的第三方客户端：优先级最高。
        //    放在黑名单之前，否则登记一个自带 okhttp / 自定义关键词的 UA 也救不回来，登记形同虚设。
        if (isAllowlistedClient(ua)) {
            chain.doFilter(request, response);
            return;
        }

        // 2) 明确为爬虫 / 无头 / 命令行工具 / 安全扫描器 → 直接拒绝
        if (UserAgentClassifier.isBotForApi(ua)) {
            writeForbidden(httpResponse);
            return;
        }

        // 3) 浏览器完整性区分拦截：识别未知 / 小众爬虫（自定义 UA、残缺或仅伪造 Mozilla 前缀）
        if (configManager.isBrowserIntegrityEnabled()) {
            // 3a) 官方原生客户端：UA 必须严格是 NekoMusic-<平台>/<版本>
            if (UserAgentClassifier.isNativeClient(ua)) {
                chain.doFilter(request, response);
                return;
            }
            // 3b) 其余必须是「UA 结构像真浏览器」且「带浏览器特征头」
            if (UserAgentClassifier.looksLikeRealBrowser(ua) && BrowserEvidence.hasFetchEvidence(httpRequest)) {
                chain.doFilter(request, response);
                return;
            }
            writeForbidden(httpResponse);
            return;
        }

        chain.doFilter(request, response);
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
            if (candidate == null) {
                continue;
            }
            String entry = candidate.trim();
            // 过短的子串（如 "app"）等于万能放行，登记端已过滤，这里再兜一层
            if (entry.length() >= MIN_ALLOWLIST_LENGTH && lower.contains(entry.toLowerCase(Locale.ROOT))) {
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

    /**
     * 需要防爬 / 客户端区分的动态接口路径：{@code /api/*} 与 {@code /loser/*}。
     * 后者是对外平台代理（歌单详情、导入），同样不能被爬虫 / 扫描器当成免费上游代理来刷。
     */
    private static boolean isGuardedPath(String path) {
        return path.startsWith("/api/") || path.startsWith("/loser/");
    }

    /** ZPay 异步通知由平台服务器回调，不参与防爬，避免通知失败（与 IP 限流豁免一致）。 */
    private static boolean isZpayNotifyPath(String path) {
        return path.equals("/api/payment/zpay/notify");
    }

    /**
     * 公开且允许 CDN 缓存的接口：内容对所有人一致，靠 CDN 缓存降低回源压力，
     * 因此不做防爬分流（爬虫拿到 JSON 也是预期行为）。
     */
    private static boolean isPublicCacheableApiPath(String path) {
        return "/api/music/ranking".equals(path) || "/api/music/latest".equals(path);
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
