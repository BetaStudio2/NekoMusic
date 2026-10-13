package com.neko.music.filter;

import com.neko.music.service.SourceBanService;
import com.neko.music.service.TestSourceBanStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 来源封禁过滤器测试：被封来源在动态接口上收到 429 + {@code Retry-After}，静态资源 / 预检 / 第三方回调
 * 不受影响；未封来源与未初始化时一律放行。
 */
class SourceBanFilterTest {

    private static final String BANNED_IP = "203.0.113.7";
    private static final String CLEAN_IP = "198.51.100.9";

    private TestSourceBanStore store;
    private SourceBanService banService;
    private SourceBanFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        store = new TestSourceBanStore();
        banService = new SourceBanService(store);
        for (int i = 0; i < SourceBanService.FAILURES_BEFORE_BAN; i++) {
            banService.recordAuthFailure(BANNED_IP);
        }

        filter = new SourceBanFilter();
        ServletContext context = proxy(ServletContext.class, (p, m, a) ->
                "getAttribute".equals(m.getName()) && "sourceBanService".equals(a[0]) ? banService : defaultValue(m));
        FilterConfig config = proxy(FilterConfig.class, (p, m, a) ->
                "getServletContext".equals(m.getName()) ? context : defaultValue(m));
        filter.init(config);
    }

    private record Result(boolean chained, int status, String retryAfter, String body) {
    }

    private Result run(String ip, String method, String path) throws Exception {
        boolean[] chained = {false};
        int[] status = {200};
        Map<String, String> headers = new HashMap<>();
        StringWriter body = new StringWriter();
        PrintWriter writer = new PrintWriter(body);

        HttpServletResponse response = proxy(HttpServletResponse.class, (p, m, a) -> {
            switch (m.getName()) {
                case "setStatus" -> status[0] = (int) a[0];
                case "setHeader" -> headers.put(((String) a[0]).toLowerCase(), (String) a[1]);
                case "getWriter" -> {
                    return writer;
                }
                default -> {
                }
            }
            return defaultValue(m);
        });

        HttpServletRequest request = proxy(HttpServletRequest.class, (p, m, a) -> switch (m.getName()) {
            case "getMethod" -> method;
            case "getRequestURI" -> path;
            case "getContextPath" -> "";
            case "getRemoteAddr" -> ip;
            case "getHeader" -> null;
            default -> defaultValue(m);
        });

        FilterChain chain = proxy(FilterChain.class, (p, m, a) -> {
            chained[0] = true;
            return defaultValue(m);
        });

        filter.doFilter(request, response, chain);
        return new Result(chained[0], status[0], headers.get("retry-after"), body.toString());
    }

    @Test
    void bannedSourceIsRejectedOnDynamicApi() throws Exception {
        Result result = run(BANNED_IP, "GET", "/api/music/latest");

        assertFalse(result.chained(), "被封来源不应进入路由");
        assertEquals(429, result.status());
        assertTrue(result.retryAfter() != null && Long.parseLong(result.retryAfter()) > 0,
                "429 必须带 Retry-After");
        assertTrue(result.body().contains("请求过于频繁"), "文案应与全局限流一致，不暴露判定规则");
    }

    @Test
    void bannedSourceIsRejectedOnLoserApiToo() throws Exception {
        Result result = run(BANNED_IP, "POST", "/loser/kugou/getSongListDetail");
        assertFalse(result.chained());
        assertEquals(429, result.status());
    }

    @Test
    void sseStreamsAreNeverBlocked() throws Exception {
        for (String path : new String[]{
                "/api/user/qrlogin/status",
                "/api/user/notifications/stream",
                "/api/admin/logs/access/stream",
                "/loser/netease/pull",
                "/loser/qq/pull",
                "/loser/kugou/pull",
                "/loser/qishui/pull"}) {
            Result result = run(BANNED_IP, "GET", path);
            assertTrue(result.chained(), "SSE 长连接不应被来源封禁挡下: " + path);
        }
    }

    @Test
    void sseExemptionToleratesTrailingSlash() throws Exception {
        Result result = run(BANNED_IP, "GET", "/api/user/notifications/stream/");
        assertTrue(result.chained(), "尾部斜杠不应绕过 SSE 豁免判定");
    }

    @Test
    void cleanSourcePassesThrough() throws Exception {
        Result result = run(CLEAN_IP, "GET", "/api/music/latest");
        assertTrue(result.chained());
        assertEquals(200, result.status());
    }

    @Test
    void staticAssetsAreNeverBlocked() throws Exception {
        for (String path : new String[]{"/media/song.mp3", "/assets/app.abc123.js", "/index.html"}) {
            Result result = run(BANNED_IP, "GET", path);
            assertTrue(result.chained(), "静态资源不应受来源封禁影响: " + path);
        }
    }

    @Test
    void preflightAndHeadAreNotBlocked() throws Exception {
        assertTrue(run(BANNED_IP, "OPTIONS", "/api/music/latest").chained());
        assertTrue(run(BANNED_IP, "HEAD", "/api/music/latest").chained());
    }

    @Test
    void thirdPartyPaymentCallbackIsNotBlocked() throws Exception {
        assertTrue(run(BANNED_IP, "POST", "/api/payment/zpay/notify").chained(),
                "支付平台回调不能因来源判定而丢失");
    }

    @Test
    void missingServiceFailsOpen() throws Exception {
        SourceBanFilter uninitialized = new SourceBanFilter();
        uninitialized.init(proxy(FilterConfig.class, (p, m, a) ->
                "getServletContext".equals(m.getName())
                        ? proxy(ServletContext.class, (p2, m2, a2) -> defaultValue(m2))
                        : defaultValue(m)));
        Result result = run(uninitialized, BANNED_IP, "GET", "/api/music/latest");
        assertTrue(result.chained(), "服务未就绪时应放行，不能把请求链路打死");
    }

    /** 用给定过滤器跑一遍请求（供「未初始化」用例复用）。 */
    private Result run(SourceBanFilter target, String ip, String method, String path) throws Exception {
        boolean[] chained = {false};
        HttpServletRequest request = proxy(HttpServletRequest.class, (p, m, a) -> switch (m.getName()) {
            case "getMethod" -> method;
            case "getRequestURI" -> path;
            case "getContextPath" -> "";
            case "getRemoteAddr" -> ip;
            case "getHeader" -> null;
            default -> defaultValue(m);
        });
        HttpServletResponse response = proxy(HttpServletResponse.class, (p, m, a) -> defaultValue(m));
        FilterChain chain = proxy(FilterChain.class, (p, m, a) -> {
            chained[0] = true;
            return defaultValue(m);
        });
        target.doFilter(request, response, chain);
        return new Result(chained[0], 200, null, "");
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Method method) {
        Class<?> returnType = method.getReturnType();
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        return null;
    }
}
