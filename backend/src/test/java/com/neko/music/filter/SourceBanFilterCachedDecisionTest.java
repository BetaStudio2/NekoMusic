package com.neko.music.filter;

import com.neko.music.service.IPRateLimitService;
import com.neko.music.service.SourceBanService;
import com.neko.music.util.RequestScope;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 来源封禁：限流过滤器已把「来源封禁」合并进同一次 Redis 判定时，本过滤器必须直接复用该结论，
 * 而不是再查一次 Redis。
 *
 * <p>这条路径涉及安全门禁，必须锁定三点：</p>
 * <ul>
 *   <li>结论为「已封禁」时照常 429，且 {@code Retry-After} 用判定带回的剩余秒数；</li>
 *   <li>结论为「放行」时不再回源查询（合并的意义就在于此）；</li>
 *   <li>存储故障（可用性优先放行）时不得误拦正常用户。</li>
 * </ul>
 */
class SourceBanFilterCachedDecisionTest {

    private static final String IP = "203.0.113.7";

    /** 记录回源次数：合并路径下必须是 0。 */
    private final AtomicInteger storeLookups = new AtomicInteger();
    private boolean storeSaysBanned;
    private SourceBanFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        SourceBanService banService = new SourceBanService(new SourceBanService.Store() {
            @Override
            public long recordFailure(String strikesKey, String levelKey, String bannedKey,
                                      int windowSeconds, int threshold, int levelQuietSeconds, int[] ladderSeconds) {
                return 0;
            }

            @Override
            public boolean isBanned(String bannedKey) {
                storeLookups.incrementAndGet();
                return storeSaysBanned;
            }

            @Override
            public long ttlSeconds(String bannedKey) {
                storeLookups.incrementAndGet();
                return storeSaysBanned ? 90 : 0;
            }

            @Override
            public void clearStrikes(String strikesKey) {
            }
        });

        filter = new SourceBanFilter();
        ServletContext context = proxy(ServletContext.class, (p, m, a) ->
                "getAttribute".equals(m.getName()) && "sourceBanService".equals(a[0]) ? banService : defaultValue(m));
        FilterConfig config = proxy(FilterConfig.class, (p, m, a) ->
                "getServletContext".equals(m.getName()) ? context : defaultValue(m));
        filter.init(config);
        RequestScope.begin();
    }

    @AfterEach
    void tearDown() {
        RequestScope.end();
    }

    private record Result(boolean chained, int status, String retryAfter) {
    }

    private Result run(String path) throws Exception {
        boolean[] chained = {false};
        int[] status = {200};
        Map<String, String> headers = new HashMap<>();
        PrintWriter writer = new PrintWriter(new StringWriter());

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
            case "getMethod" -> "GET";
            case "getRequestURI" -> path;
            case "getContextPath" -> "";
            case "getRemoteAddr" -> IP;
            case "getHeader" -> null;
            default -> defaultValue(m);
        });

        FilterChain chain = proxy(FilterChain.class, (p, m, a) -> {
            chained[0] = true;
            return defaultValue(m);
        });

        filter.doFilter(request, response, chain);
        return new Result(chained[0], status[0], headers.get("retry-after"));
    }

    @Test
    void cachedBannedDecisionIsEnforcedWithoutExtraLookup() throws Exception {
        RequestScope.put(IPRateLimitFilter.GUARD_DECISION_KEY,
                new IPRateLimitService.Decision(IPRateLimitService.Decision.SOURCE_BANNED, 90));

        Result result = run("/api/music/latest");

        assertFalse(result.chained(), "已封禁来源不应进入路由");
        assertEquals(429, result.status());
        assertEquals("90", result.retryAfter(), "应使用判定带回的剩余秒数");
        assertEquals(0, storeLookups.get(), "已判定过就不该再查一次 Redis");
    }

    @Test
    void cachedAllowedDecisionSkipsLookup() throws Exception {
        RequestScope.put(IPRateLimitFilter.GUARD_DECISION_KEY,
                new IPRateLimitService.Decision(IPRateLimitService.Decision.ALLOWED, 0));

        Result result = run("/api/music/latest");

        assertTrue(result.chained());
        assertEquals(200, result.status());
        assertEquals(0, storeLookups.get());
    }

    @Test
    void cachedStorageErrorIsFailOpen() throws Exception {
        RequestScope.put(IPRateLimitFilter.GUARD_DECISION_KEY,
                new IPRateLimitService.Decision(IPRateLimitService.Decision.UNAVAILABLE, 0));

        Result result = run("/api/music/latest");

        assertTrue(result.chained(), "存储不可用时应放行（与各限流的失败策略一致）");
        assertEquals(0, storeLookups.get());
    }

    @Test
    void withoutCachedDecisionFallsBackToItsOwnCheck() throws Exception {
        storeSaysBanned = true;

        Result result = run("/api/music/latest");

        assertFalse(result.chained(), "没有合并结论时仍要自己判定封禁");
        assertEquals(429, result.status());
        assertTrue(storeLookups.get() > 0, "回退路径应查询存储");
    }

    @Test
    void withoutCachedDecisionCleanSourcePasses() throws Exception {
        Result result = run("/api/music/latest");

        assertTrue(result.chained());
        assertTrue(storeLookups.get() > 0);
    }

    // ---------- 代理工具 ----------

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static Object defaultValue(Method method) {
        Class<?> type = method.getReturnType();
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == void.class) {
            return null;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == char.class) {
            return (char) 0;
        }
        return 0;
    }
}
