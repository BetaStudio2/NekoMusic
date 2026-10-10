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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 动态接口防爬行为测试：/api 与 /loser 上的爬虫 / 扫描器 / 伪造客户端命中后直接 403
 * （不在接口路径上渲染 SEO 页），真实浏览器 / 已登记客户端 / 官方原生客户端放行，
 * 公开缓存接口与静态媒体路径不受影响。
 *
 * <p>用动态代理桩替代 Servlet 容器，不依赖 MySQL/Redis。</p>
 */
class CrawlerProtectionFilterTest {

    private static final String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0.0.0 Safari/537.36";

    private record Outcome(int status, boolean chained, String forwarded, String body, String vary,
            String cacheControl) {
    }

    private Outcome inspect(String ua, Map<String, String> headers) throws Exception {
        return inspect("GET", "/api/music/info/1", ua, headers);
    }

    private Outcome inspect(String method, String uri, String ua, Map<String, String> headers) throws Exception {
        return inspect(new ConfigManager(), method, uri, ua, headers);
    }

    private Outcome inspect(ConfigManager config, String method, String uri, String ua,
                            Map<String, String> headers) throws Exception {
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
    void rejectsKnownBotsAndScanners() throws Exception {
        Outcome curl = inspect("curl/8.5.0", null);
        assertEquals(403, curl.status());
        assertNull(curl.forwarded());
        assertFalse(curl.body().contains("SEO"));
        assertFalse(curl.body().contains("<html"));
        assertEquals("private, no-store", curl.cacheControl());
        assertFalse(curl.chained());

        for (String botUa : new String[]{"python-requests/2.31.0", "sqlmap/1.7.2#stable",
                "Mozilla/5.00 (Nikto/2.5.0)", "Mozilla/5.0 zgrab/0.x"}) {
            Outcome outcome = inspect(botUa, null);
            assertEquals(403, outcome.status(), botUa);
            assertNull(outcome.forwarded(), botUa);
            assertFalse(outcome.chained(), botUa);
        }
    }

    @Test
    void rejectsUnknownCrawlersWithCustomOrSpoofedUserAgent() throws Exception {
        for (String crawlerUa : new String[]{"MyCollector/1.0", "Mozilla/5.0 (compatible; AcmeIndex/1.0)",
                "Mozilla/5.0", "Mozilla/5.0 (X11; Linux x86_64)",
                "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1)"}) {
            Outcome outcome = inspect(crawlerUa, null);
            assertEquals(403, outcome.status(), crawlerUa);
            assertNull(outcome.forwarded(), crawlerUa);
            assertFalse(outcome.chained(), crawlerUa);
        }
    }

    @Test
    void treatsMissingUserAgentAsCrawlerAndRejectsCompleteUaVariants() throws Exception {
        // 空 UA 一律按爬虫处理：直接 403
        for (String noUa : new String[]{null, "", "   "}) {
            Outcome outcome = inspect(noUa, null);
            assertEquals(403, outcome.status());
            assertNull(outcome.forwarded());
            assertFalse(outcome.chained());
        }
        // 空 UA 的写请求直接 403
        Outcome post = inspect("POST", "/api/user/login", null, null);
        assertEquals(403, post.status());
        assertNull(post.forwarded());
        // 官方 UA 必须带平台与版本：裸前缀 / 空格写法一律拒绝
        Outcome coverUa = inspect("NekoMusic Qt", Map.of(
                "Accept", "image/png,image/jpeg,image/*;q=0.8,*/*;q=0.5"));
        assertEquals(403, coverUa.status());
        assertNull(coverUa.forwarded());
        assertFalse(coverUa.chained());
    }

    @Test
    void rejectsOfficialPrefixWithoutPlatformOrVersion() throws Exception {
        for (String ua : new String[]{"NekoMusic-android", "NekoMusic-android/", "NekoMusic Android",
                "NekoMusic-android/abc", "NekoMusic-PC", "NekoMusic-android/202601008/extra"}) {
            Outcome outcome = inspect(ua, Map.of("Accept", "*/*"));
            assertEquals(403, outcome.status(), ua);
            assertNull(outcome.forwarded(), ua);
            assertFalse(outcome.chained(), ua);
        }
        // 合法形式照常放行
        assertTrue(inspect("NekoMusic-android/202601008", null).chained());
        assertTrue(inspect("NekoMusic-PC/2026.108.52", null).chained());
        assertTrue(inspect("nekomusic-android/1.0.0", null).chained());
    }

    @Test
    void rejectsBrowserUserAgentSpoofWithoutBrowserHeaders() throws Exception {
        Outcome noHeaders = inspect(BROWSER_UA, null);
        assertEquals(403, noHeaders.status());
        assertNull(noHeaders.forwarded());
        assertFalse(noHeaders.chained());

        Outcome acceptOnly = inspect(BROWSER_UA, Map.of("Accept", "application/json"));
        assertEquals(403, acceptOnly.status());
        assertNull(acceptOnly.forwarded());
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
    void allowsOnlyOfficialNativeClientUserAgents() throws Exception {
        Outcome android = inspect("NekoMusic-android/202601008", null);
        assertEquals(200, android.status());
        assertTrue(android.chained());
        assertNull(android.forwarded());

        Outcome pc = inspect("NekoMusic-PC/1.0", null);
        assertTrue(pc.chained());

        // 播放器 / 通用 HTTP 栈 UA 不再豁免 /api（它们只取 /media/* 直链）：直接 403
        for (String playerUa : new String[]{"okhttp/4.12.0", "Dalvik/2.1.0 (Linux; U; Android 13)", "libmpv/0.36"}) {
            Outcome outcome = inspect(playerUa, null);
            assertEquals(403, outcome.status(), playerUa);
            assertNull(outcome.forwarded(), playerUa);
            assertFalse(outcome.chained(), playerUa);
        }
    }

    @Test
    void nativeKeywordInSpoofedUserAgentDoesNotBypass() throws Exception {
        for (String spoofed : new String[]{"sqlmap android", "python-requests/2.31.0 Android", "curl/8.5.0 dalvik"}) {
            Outcome outcome = inspect(spoofed, null);
            assertEquals(403, outcome.status(), spoofed);
            assertNull(outcome.forwarded(), spoofed);
            assertFalse(outcome.chained(), spoofed);
        }
    }

    @Test
    void configAllowlistTakesPrecedenceOverBlacklist() throws Exception {
        ConfigManager config = new ConfigManager();
        var field = ConfigManager.class.getDeclaredField("apiClientAllowlist");
        field.setAccessible(true);
        field.set(config, List.of("thirdparty-client/2.0"));

        Outcome allowlisted = inspect(config, "GET", "/api/music/info/1",
                "okhttp/4.12.0 thirdparty-client/2.0", null);
        assertTrue(allowlisted.chained());

        // 过短的登记项等于万能放行，必须被忽略
        ConfigManager shortEntry = new ConfigManager();
        field.set(shortEntry, List.of("app"));
        assertFalse(inspect(shortEntry, "GET", "/api/music/info/1", "okhttp/4.12.0", null).chained());
    }

    @Test
    void nonGetCrawlerRequestIsForbidden() throws Exception {
        Outcome post = inspect("POST", "/api/user/login", "curl/8.5.0", null);
        assertEquals(403, post.status());
        assertNull(post.forwarded());
    }

    @Test
    void publicCacheableEndpointsSkipCrawlerDivertion() throws Exception {
        // 排行榜 / 最新音乐是公开缓存接口（CDN 缓存半小时），对所有人返回同一份 JSON：
        // 不参与防爬分流，否则缓存命中与否会让同一个 UA 时而被拦、时而拿到 JSON。
        for (String path : new String[]{"/api/music/ranking", "/api/music/latest"}) {
            for (String ua : new String[]{"curl/8.5.0", "sqlmap android", "okhttp/4.12.0", null}) {
                Outcome outcome = inspect("GET", path, ua, null);
                assertTrue(outcome.chained(), path + " / " + ua);
                assertNull(outcome.forwarded(), path + " / " + ua);
            }
        }
    }

    @Test
    void externalPlatformProxyPathsAreProtectedToo() throws Exception {
        // /loser/* 是对外平台代理（歌单详情 / 导入）：不能让爬虫与扫描器把它当成免费上游代理来刷
        for (String path : new String[]{"/loser/kugou/getSongListDetail", "/loser/qq/getSongListDetail",
                "/loser/qishui/getSongListDetail", "/loser/netease/search"}) {
            Outcome googlebot = inspect("GET", path, "Googlebot/2.1 (+http://www.google.com/bot.html)", null);
            assertEquals(403, googlebot.status(), path);
            assertNull(googlebot.forwarded(), path);
            assertFalse(googlebot.chained(), path);

            Outcome scanner = inspect("GET", path, "sqlmap/1.7.2#stable", null);
            assertEquals(403, scanner.status(), path);
            assertFalse(scanner.chained(), path);

            Outcome noUa = inspect("GET", path, null, null);
            assertEquals(403, noUa.status(), path);
            assertFalse(noUa.chained(), path);
        }

        // 官方原生客户端与真实浏览器照常放行
        assertTrue(inspect("GET", "/loser/kugou/getSongListDetail", "NekoMusic-android/202601008", null)
                .chained());
        assertTrue(inspect("GET", "/loser/kugou/getSongListDetail", BROWSER_UA, Map.of(
                "Accept", "application/json, text/plain, */*",
                "Accept-Language", "zh-CN,zh;q=0.9")).chained());
    }

    @Test
    void publicMediaPathsAreNotSubjectToApiCrawlerBlock() throws Exception {
        // SEO 页的 og:image / og:audio 引用公开静态媒体，爬虫与链接预览必须能直接抓到
        for (String path : new String[]{"/media/cover/1", "/media/music/1"}) {
            for (String ua : new String[]{"curl/8.5.0", "facebookexternalhit/1.1", null}) {
                Outcome outcome = inspect("GET", path, ua, null);
                assertTrue(outcome.chained(), path + " / " + ua);
                assertNull(outcome.forwarded(), path + " / " + ua);
            }
        }
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
