package com.neko.music.filter;

import com.neko.music.config.ConfigManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * /api 防爬与「爬虫直出 SEO」的行为测试。
 *
 * <p>用动态代理桩替代 Servlet 容器，不依赖 MySQL/Redis，聚焦过滤器判定：
 * 爬虫 GET / HEAD 直接 forward 到对应 SEO 页并返回 200（不再 302），
 * 其余方法 403，真实浏览器 / 原生客户端放行。</p>
 */
class CrawlerProtectionFilterTest {

    private static final String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0.0.0 Safari/537.36";

    private record Outcome(int status, boolean chained, String forwarded, String body, String vary,
            String cacheControl) {
    }

    private Outcome inspect(String ua, Map<String, String> headers) throws Exception {
        return inspect("GET", "/api/music/ranking", ua, headers);
    }

    private Outcome inspect(String method, String uri, String ua, Map<String, String> headers) throws Exception {
        ConfigManager config = new ConfigManager();
        CrawlerProtectionFilter filter = new CrawlerProtectionFilter();

        ServletContext ctx = proxy(ServletContext.class, (p, m, a) ->
                "getAttribute".equals(m.getName()) ? config : defaultValue(m));
        FilterConfig fc = proxy(FilterConfig.class, (p, m, a) ->
                "getServletContext".equals(m.getName()) ? ctx : defaultValue(m));
        filter.init(fc);

        Map<String, String> hs = new HashMap<>();
        if (headers != null) {
            headers.forEach((k, v) -> hs.put(k.toLowerCase(), v));
        }
        if (ua != null) {
            hs.put("user-agent", ua);
        }

        int[] status = {200};
        Map<String, String> responseHeaders = new HashMap<>();
        StringWriter responseBody = new StringWriter();
        PrintWriter writer = new PrintWriter(responseBody);
        HttpServletResponse resp = proxy(HttpServletResponse.class, (p, m, a) -> {
            switch (m.getName()) {
                case "setStatus" -> status[0] = (int) a[0];
                case "setHeader" -> responseHeaders.put(((String) a[0]).toLowerCase(), (String) a[1]);
                case "getWriter" -> {
                    return writer;
                }
                default -> {
                }
            }
            return defaultValue(m);
        });

        // 桩 dispatcher：记录 forward 目标并写入可见正文，用来断言「直出 SEO 页」而不是 302。
        String[] forwarded = {null};
        RequestDispatcher dispatcher = proxy(RequestDispatcher.class, (p, m, a) -> {
            if ("forward".equals(m.getName())) {
                ((HttpServletResponse) a[1]).getWriter().write("<html>SEO</html>");
            }
            return defaultValue(m);
        });

        HttpServletRequest req = proxy(HttpServletRequest.class, (p, m, a) -> switch (m.getName()) {
            case "getRequestURI" -> uri;
            case "getContextPath" -> "";
            case "getMethod" -> method;
            case "getRemoteAddr" -> "127.0.0.1";
            case "getHeader" -> hs.get(((String) a[0]).toLowerCase());
            case "getRequestDispatcher" -> {
                forwarded[0] = (String) a[0];
                yield dispatcher;
            }
            default -> defaultValue(m);
        });

        boolean[] chained = {false};
        FilterChain chain = proxy(FilterChain.class, (p, m, a) -> {
            if ("doFilter".equals(m.getName())) {
                chained[0] = true;
            }
            return defaultValue(m);
        });

        filter.doFilter(req, resp, chain);
        return new Outcome(status[0], chained[0], forwarded[0], responseBody.toString(),
                responseHeaders.get("vary"), responseHeaders.get("cache-control"));
    }

    @Test
    void divertsKnownBotsAndScannersToSeo() throws Exception {
        Outcome curl = inspect("curl/8.5.0", null);
        assertEquals(200, curl.status());
        assertEquals("/ranking", curl.forwarded());
        assertEquals("<html>SEO</html>", curl.body());
        assertEquals("User-Agent", curl.vary());
        assertEquals("private, no-store", curl.cacheControl());
        assertFalse(curl.chained());

        for (String botUa : new String[]{"python-requests/2.31.0", "sqlmap/1.7.2#stable",
                "Mozilla/5.00 (Nikto/2.5.0)", "Mozilla/5.0 zgrab/0.x"}) {
            Outcome outcome = inspect(botUa, null);
            assertEquals(200, outcome.status(), botUa);
            assertEquals("/ranking", outcome.forwarded(), botUa);
            assertFalse(outcome.chained(), botUa);
        }
    }

    @Test
    void divertsUnknownCrawlersWithCustomOrSpoofedUserAgentToSeo() throws Exception {
        for (String crawlerUa : new String[]{"MyCollector/1.0", "Mozilla/5.0 (compatible; AcmeIndex/1.0)",
                "Mozilla/5.0", "Mozilla/5.0 (X11; Linux x86_64)",
                "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1)"}) {
            Outcome outcome = inspect(crawlerUa, null);
            assertEquals(200, outcome.status(), crawlerUa);
            assertEquals("/ranking", outcome.forwarded(), crawlerUa);
            assertFalse(outcome.chained(), crawlerUa);
        }
    }

    @Test
    void allowsDesktopQtClientThatSendsNoUserAgentOrNekoMusicUa() throws Exception {
        // NekoMusic PC 的 ApiClient 用 QNetworkRequest，默认不发送 UA
        Outcome noUa = inspect(null, null);
        assertEquals(200, noUa.status());
        assertTrue(noUa.chained());
        // 封面请求 UA = "NekoMusic Qt"
        Outcome coverUa = inspect("NekoMusic Qt", Map.of(
                "Accept", "image/png,image/jpeg,image/*;q=0.8,*/*;q=0.5"));
        assertEquals(200, coverUa.status());
        assertTrue(coverUa.chained());
    }

    @Test
    void divertsBrowserUserAgentSpoofWithoutBrowserHeaders() throws Exception {
        Outcome noHeaders = inspect(BROWSER_UA, null);
        assertEquals(200, noHeaders.status());
        assertEquals("/ranking", noHeaders.forwarded());
        assertFalse(noHeaders.chained());

        Outcome acceptOnly = inspect(BROWSER_UA, Map.of("Accept", "application/json"));
        assertEquals(200, acceptOnly.status());
        assertEquals("/ranking", acceptOnly.forwarded());
        assertFalse(acceptOnly.chained());
    }

    @Test
    void allowsRealBrowserWithFetchEvidence() throws Exception {
        Outcome withLang = inspect(BROWSER_UA, Map.of(
                "Accept", "application/json, text/plain, */*",
                "Accept-Language", "zh-CN,zh;q=0.9"));
        assertEquals(200, withLang.status());
        assertTrue(withLang.chained());

        // 老浏览器可能没有 Sec-Fetch-*，只要 Accept-Language 即可
        Outcome secFetchOnly = inspect(BROWSER_UA, Map.of(
                "Accept", "*/*",
                "Sec-Fetch-Mode", "cors",
                "Sec-Fetch-Site", "same-origin"));
        assertEquals(200, secFetchOnly.status());
        assertTrue(secFetchOnly.chained());
    }

    @Test
    void allowsNativeClients() throws Exception {
        assertEquals(200, inspect("okhttp/4.12.0", null).status());
        assertEquals(200, inspect("Dalvik/2.1.0 (Linux; U; Android 13)", null).status());
        assertEquals(200, inspect("libmpv/0.36", null).status());
        assertEquals(200, inspect(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) NekoMusicPC/1.0 QtWebEngine/6.6.0", null).status());
    }

    @Test
    void nonGetCrawlerRequestIsForbidden() throws Exception {
        Outcome post = inspect("POST", "/api/user/login", "curl/8.5.0", null);
        assertEquals(403, post.status());
        assertNull(post.forwarded());
    }

    @Test
    void mapsApiPathsToSeoPages() {
        assertEquals("/ranking", CrawlerProtectionFilter.seoPageForApiPath("/api/music/ranking"));
        assertEquals("/latest", CrawlerProtectionFilter.seoPageForApiPath("/api/music/latest"));
        assertEquals("/search", CrawlerProtectionFilter.seoPageForApiPath("/api/music/search"));
        assertEquals("/detail/42", CrawlerProtectionFilter.seoPageForApiPath("/api/music/info/42"));
        assertEquals("/detail/42", CrawlerProtectionFilter.seoPageForApiPath("/api/music/cover/42"));
        assertEquals("/detail/42", CrawlerProtectionFilter.seoPageForApiPath("/api/music/file/42"));
        assertEquals("/detail/7", CrawlerProtectionFilter.seoPageForApiPath("/api/music/lyrics/7"));
        assertEquals("/", CrawlerProtectionFilter.seoPageForApiPath("/api/user/login"));
        assertEquals("/", CrawlerProtectionFilter.seoPageForApiPath("/api/music/search/abc"));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    /** 代理的默认返回值：基本类型给零值，其余给 null（避免 equals/hashCode 返回 null 触发 NPE）。 */
    private static Object defaultValue(Method method) {
        Class<?> rt = method.getReturnType();
        if (!rt.isPrimitive()) {
            return null;
        }
        if (rt == boolean.class) {
            return false;
        }
        if (rt == int.class) {
            return 0;
        }
        if (rt == long.class) {
            return 0L;
        }
        if (rt == short.class) {
            return (short) 0;
        }
        if (rt == byte.class) {
            return (byte) 0;
        }
        if (rt == char.class) {
            return (char) 0;
        }
        if (rt == float.class) {
            return 0f;
        }
        if (rt == double.class) {
            return 0d;
        }
        return null;
    }
}
