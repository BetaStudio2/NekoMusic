package com.neko.music.filter;

import com.neko.music.service.ReplayNonceService;
import com.neko.music.service.TestNonceStore;
import com.neko.music.util.PowAudit;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通用防重放过滤器的判定测试：动态接口缺 nonce → 409，nonce 只能消费一次（重放 409），
 * 豁免清单（原生 &lt;img&gt;、SSE、公开可缓存接口、multipart、预检）必须放行。
 *
 * <p>用动态代理桩替代 Servlet 容器，不依赖 MySQL/Redis。</p>
 */
class ReplayProtectionFilterTest {

    private static TestNonceStore redis;

    @BeforeAll
    static void installStore() {
        redis = new TestNonceStore();
        ReplayNonceService.useStore(redis);
    }

    @AfterAll
    static void restoreStore() {
        ReplayNonceService.useStore(null);
    }

    @BeforeEach
    void reset() {
        redis.setDown(false);
        redis.clear();
    }

    private record Outcome(int status, boolean chained, String body, String replayStatus, String cacheControl,
                           Integer powDifficulty, String powStatus) {
    }

    private Outcome run(String method, String uri, String nonce, boolean multipart) throws Exception {
        return run(method, uri, nonce, multipart ? "multipart/form-data; boundary=----x" : "application/json");
    }

    private Outcome run(String method, String uri, String nonce, String contentType) throws Exception {
        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put("user-agent", "NekoMusic-Test/1.0");
        requestHeaders.put("x-neko-client", "web+test");
        if (nonce != null) {
            requestHeaders.put(ReplayNonceService.NONCE_HEADER.toLowerCase(), nonce);
        }
        Map<String, String> responseHeaders = new HashMap<>();
        Map<String, Object> attributes = new HashMap<>();
        int[] status = {200};
        StringWriter body = new StringWriter();
        PrintWriter writer = new PrintWriter(body);
        HttpServletResponse response = proxy(HttpServletResponse.class, (p, m, a) -> {
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

        HttpServletRequest request = proxy(HttpServletRequest.class, (p, m, a) -> switch (m.getName()) {
            case "getMethod" -> method;
            case "getRequestURI" -> uri;
            case "getContextPath" -> "";
            case "getRemoteAddr" -> "127.0.0.1";
            case "getContentType" -> contentType;
            case "getHeader" -> requestHeaders.get(((String) a[0]).toLowerCase());
            // 请求属性是 PoW 难度 / 结果的传递通道，桩里必须真的存下来才能断言
            case "setAttribute" -> {
                attributes.put((String) a[0], a[1]);
                yield null;
            }
            case "getAttribute" -> attributes.get((String) a[0]);
            default -> defaultValue(m);
        });

        boolean[] chained = {false};
        FilterChain chain = proxy(FilterChain.class, (p, m, a) -> {
            if ("doFilter".equals(m.getName())) {
                chained[0] = true;
            }
            return defaultValue(m);
        });

        new ReplayProtectionFilter().doFilter(request, response, chain);
        return new Outcome(status[0], chained[0], body.toString(),
                responseHeaders.get(ReplayProtectionFilter.REPLAY_STATUS_HEADER.toLowerCase()),
                responseHeaders.get("cache-control"),
                (Integer) attributes.get(PowAudit.ATTR_DIFFICULTY),
                (String) attributes.get(PowAudit.ATTR_STATUS));
    }

    private static String readNonce() {
        return ReplayNonceService.issue(
                ReplayNonceService.SCOPE_READ, 1, "NekoMusic-Test/1.0", "web+test", 0).get(0);
    }

    private static String writeNonce() {
        return ReplayNonceService.issue(
                ReplayNonceService.SCOPE_WRITE, 1, "NekoMusic-Test/1.0", "web+test", 0).get(0);
    }

    @Test
    void dynamicRequestWithoutNonceIsRejected() throws Exception {
        Outcome outcome = run("GET", "/api/music/search", null, false);
        assertEquals(409, outcome.status());
        assertFalse(outcome.chained());
        assertEquals(ReplayProtectionFilter.STATUS_MISSING, outcome.replayStatus());
        assertEquals("private, no-store", outcome.cacheControl());
        assertTrue(outcome.body().contains("\"success\":false"));
    }

    @Test
    void freshNonceIsAcceptedAndReplayIsRejected() throws Exception {
        String nonce = readNonce();
        Outcome first = run("GET", "/api/music/search", nonce, false);
        assertTrue(first.chained());

        // 完全相同的请求再发一次 = 重放
        Outcome replay = run("GET", "/api/music/search", nonce, false);
        assertFalse(replay.chained());
        assertEquals(409, replay.status());
        assertEquals(ReplayProtectionFilter.STATUS_INVALID, replay.replayStatus());
    }

    @Test
    void writeRequestCannotBorrowReadNonce() throws Exception {
        Outcome crossScope = run("POST", "/api/comments", readNonce(), false);
        assertFalse(crossScope.chained());
        assertEquals(ReplayProtectionFilter.STATUS_INVALID, crossScope.replayStatus());

        Outcome ok = run("POST", "/api/comments", writeNonce(), false);
        assertTrue(ok.chained());
    }

    @Test
    void costlyReadEndpointIsProtected() throws Exception {
        assertFalse(run("GET", "/api/music/file/42", null, false).chained());
        assertTrue(run("GET", "/api/music/file/42", readNonce(), false).chained());
    }

    @Test
    void exemptPathsPassWithoutNonce() throws Exception {
        // nonce 签发接口自身
        assertTrue(run("GET", "/api/replay/nonce", null, false).chained());
        assertTrue(run("GET", "/api/replay/nonce/", null, false).chained());
        // 换取挑战（领取 nonce 的第一步）同样豁免
        assertTrue(run("GET", "/api/replay/challenge", null, false).chained());
        // 公开缓存接口（handler 内覆盖为 public, max-age=1800，重放无副作用）
        assertTrue(run("GET", "/api/music/latest", null, false).chained());
        assertTrue(run("GET", "/api/music/ranking", null, false).chained());
        // 浏览器 <img src> 直接加载
        assertTrue(run("GET", "/api/user/avatar/42", null, false).chained());
        assertTrue(run("GET", "/api/music/cover/42", null, false).chained());
        // EventSource(SSE) 扫码登录状态流
        assertTrue(run("GET", "/api/user/qrlogin/status", null, false).chained());
        // 扫码登录的非 SSE 接口仍受保护
        assertFalse(run("POST", "/api/user/qrlogin/confirm", null, false).chained());
        // SSE 歌单导入进度流
        assertTrue(run("GET", "/loser/netease/pull", null, false).chained());
        // 第三方支付回调
        assertTrue(run("POST", "/api/payment/zpay/notify", null, false).chained());
        // 审核页媒体预览：CDN 按 Range 分片回源时后续请求不带自定义头
        assertTrue(run("GET", "/api/user/upload/preview", null, false).chained());
        assertTrue(run("GET", "/api/user/upload/preview/", null, false).chained());
    }

    @Test
    void multipartUploadEndpointsAreExemptButJsonOnSamePathIsNot() throws Exception {
        for (String path : new String[]{"/api/music/upload", "/api/music/recognize",
                "/api/user/upload", "/api/user/avatar/upload", "/api/admin/releases/upload"}) {
            assertTrue(run("POST", path, null, true).chained(), "真实上传端点应豁免: " + path);
        }
        // 上传端点上的非 multipart 请求仍要 nonce（/api/user/avatar/* 整体豁免是另一条规则，不在此列）
        for (String path : new String[]{"/api/music/upload", "/api/music/recognize",
                "/api/user/upload", "/api/admin/releases/upload"}) {
            assertFalse(run("POST", path, null, false).chained(),
                    "上传端点上的非 multipart 请求仍要 nonce: " + path);
        }
    }

    @Test
    void multipartContentTypeCannotExemptArbitraryProtectedPaths() throws Exception {
        // 回归：Content-Type 完全由客户端控制，不能拿它当「免检」开关——否则任意受保护接口
        // 加一行假头就能跳过 nonce 校验（下游 handler 读 body 时并不看内容类型）
        for (String path : new String[]{"/api/admin/login", "/api/admin/settings",
                "/api/user/info", "/api/music/search", "/loser/kugou/share"}) {
            assertFalse(run("POST", path, null, true).chained(),
                    "multipart 头不能跳过 nonce 校验: " + path);
        }
        // GET 同样不生效：门禁只看请求头，与是否携带请求体无关
        assertFalse(run("GET", "/api/user/info", null, true).chained());
        assertFalse(run("GET", "/api/admin/settings", null, true).chained());
        // 大小写变体也不能绕过
        assertFalse(run("POST", "/api/admin/login", null, "MULTIPART/FORM-DATA; boundary=x").chained());
    }

    @Test
    void preflightAndHeadAndNonDynamicPathsPass() throws Exception {
        assertTrue(run("OPTIONS", "/api/comments", null, false).chained());
        assertTrue(run("HEAD", "/api/comments", null, false).chained());
        // 静态媒体：CDN 共享缓存，协议上无法逐请求校验
        assertTrue(run("GET", "/media/music/abc.mp3", null, false).chained());
        assertTrue(run("GET", "/assets/index-abc.js", null, false).chained());
        // SEO 详情页是浏览器导航，不带自定义头
        assertTrue(run("GET", "/detail/music/42", null, false).chained());
    }

    @Test
    void powDifficultyOfTheConsumedNonceIsRecordedForSecurityAnalysis() throws Exception {
        // 签发难度 24 的一枚 nonce：受保护请求消费它时，访问日志要能写出「这次请求花了多少算力」
        String nonce = ReplayNonceService.issue(
                ReplayNonceService.SCOPE_READ, 1, "NekoMusic-Test/1.0", "web+test", 24).get(0);
        Outcome accepted = run("GET", "/api/music/search", nonce, false);
        assertTrue(accepted.chained());
        assertEquals(PowAudit.VERIFIED, accepted.powStatus());
        assertEquals(24, accepted.powDifficulty());

        // 缺 nonce：难度未知，只留痕失败类别
        Outcome missing = run("GET", "/api/music/search", null, false);
        assertEquals(PowAudit.MISSING, missing.powStatus());
        assertNull(missing.powDifficulty());

        // 重放同一枚 nonce：记录为非法的 nonce（难度随之无从得知）
        Outcome replay = run("GET", "/api/music/search", nonce, false);
        assertEquals(PowAudit.INVALID, replay.powStatus());
        assertNull(replay.powDifficulty());
    }

    @Test
    void storeFailureFailsOpenWithWarning() throws Exception {
        redis.setDown(true);
        Outcome outcome = run("GET", "/api/music/search", "0123456789abcdef0123456789abcdef", false);
        assertTrue(outcome.chained(), "存储故障时应放行（可用性优先）");
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    /** 代理的默认返回值：基本类型给零值，其余给 null。 */
    private static Object defaultValue(Method method) {
        Class<?> type = method.getReturnType();
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == char.class) {
            return (char) 0;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == double.class) {
            return 0d;
        }
        return null;
    }

    @Test
    void equivalentPathSpellingsNeedNonceToo() throws Exception {
        // 这些写法会被 Jetty 规范化后路由到真实 handler：若按原始 URI 判定就会被当成非受保护路径放行。
        for (String path : new String[]{"/./api/music/search", "/a/../api/music/search",
                "/api;/music/search", "/%61pi/music/search", "/api/music/search;jsessionid=x",
                "/api/music/../music/search"}) {
            Outcome outcome = run("GET", path, null, false);
            assertEquals(409, outcome.status(), path);
            assertFalse(outcome.chained(), path);
        }
        for (String path : new String[]{"/./loser/kugou/getSongListDetail",
                "/a/../loser/qq/getSongListDetail", "/loser;/kugou/getSongListDetail"}) {
            Outcome outcome = run("GET", path, null, false);
            assertEquals(409, outcome.status(), path);
            assertFalse(outcome.chained(), path);
        }
    }

    @Test
    void replayIsStillRejectedWhenThePathSpellingChanges() throws Exception {
        String nonce = readNonce();
        assertTrue(run("GET", "/api/music/search", nonce, false).chained());

        // 同一个 nonce 换等价写法重放：nonce 已被消费，必须仍然 409
        Outcome replay = run("GET", "/./api/music/search", nonce, false);
        assertEquals(409, replay.status());
        assertFalse(replay.chained());
    }

    @Test
    void equivalentSpellingsOfExemptPathsKeepTheirTreatment() throws Exception {
        // 规范化后仍落在豁免清单：不能被新判定误伤（否则客户端连 nonce 都领不到）
        for (String path : new String[]{"/./api/replay/nonce", "/a/../api/replay/challenge",
                "/api/music/latest", "/./api/music/ranking", "/./loser/kugou/pull"}) {
            Outcome outcome = run("GET", path, null, false);
            assertTrue(outcome.chained(), path);
        }
    }
}
