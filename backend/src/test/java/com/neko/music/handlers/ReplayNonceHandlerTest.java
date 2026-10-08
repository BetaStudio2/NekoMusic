package com.neko.music.handlers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.neko.music.service.ReplayChallengeService;
import com.neko.music.service.ReplayChallengeSolver;
import com.neko.music.service.ReplayIssueLimiter;
import com.neko.music.service.ReplayNonceService;
import com.neko.music.service.TestNonceStore;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 签发接口的行为测试：挑战 → 兑换 nonce 的两步链路、各类被拒情形统一回应（不透露原因），
 * 以及签发限额对被刷取的封顶。
 *
 * <p>用动态代理桩替代 Servlet 容器，不依赖 Redis。</p>
 */
class ReplayNonceHandlerTest {

    private static final String IP = "127.0.0.1";
    private static final String UA = "NekoMusic-android/202601008";
    /** 冻住限额器时钟，让「打到突发上限」这件事可复现（不依赖真实流逝的毫秒）。 */
    private static final long FROZEN_MILLIS = 1_700_000_000_000L;

    private static TestNonceStore store;

    @BeforeAll
    static void installStore() {
        store = new TestNonceStore();
        ReplayNonceService.useStore(store);
        ReplayIssueLimiter.useClock(() -> FROZEN_MILLIS);
    }

    @AfterAll
    static void restoreStore() {
        ReplayNonceService.useStore(null);
        ReplayIssueLimiter.useClock(null);
        ReplayIssueLimiter.reset();
    }

    @BeforeEach
    void reset() {
        store.clear();
        store.setDown(false);
        ReplayChallengeService.reset();
        ReplayIssueLimiter.reset();
    }

    private record Result(int status, String body) {
    }

    @Test
    void challengeFlowIssuesNoncesOnceAndRejectsReplay() throws Exception {
        JsonObject challenge = challengeData(4, 4);
        String id = challenge.get("challenge").getAsString();
        assertEquals(ReplayChallengeService.ALGORITHM, challenge.get("algorithm").getAsString());

        Map<String, String> params = new HashMap<>();
        params.put("challenge", id);
        params.put("proof", solveChallenge(challenge));
        Result granted = runNonce(params);
        assertEquals(200, granted.status());
        JsonObject nonces = JsonParser.parseString(granted.body()).getAsJsonObject()
                .getAsJsonObject("data").getAsJsonObject("nonces");
        assertEquals(4, nonces.getAsJsonArray("read").size());
        assertEquals(4, nonces.getAsJsonArray("write").size());

        // 同一道题再兑换（重放）→ 409
        assertEquals(409, runNonce(params).status());
    }

    @Test
    void badProofIsRejectedWith409() throws Exception {
        JsonObject data = challengeData(1, 1);
        Map<String, String> params = new HashMap<>();
        params.put("challenge", data.get("challenge").getAsString());
        params.put("proof", "1");
        Result result = runNonce(params);
        assertEquals(409, result.status());
        assertTrue(result.body().contains("\"success\":false"));
    }

    @Test
    void unknownChallengeIsRejectedWith409() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("challenge", "0123456789abcdef0123456789abcdef");
        params.put("proof", "12345");
        Result result = runNonce(params);
        assertEquals(409, result.status());
        assertTrue(result.body().contains("\"success\":false"));
    }

    @Test
    void directClaimWithoutChallengeIsRejected() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("read", "64");
        params.put("write", "64");

        // 收口后：不带挑战一律拒绝，领取不能再被脚本免解题刷取
        Result result = runNonce(params);
        assertEquals(409, result.status());
        assertTrue(result.body().contains("\"success\":false"));
    }

    /**
     * 关键性质：缺挑战、题目不存在、解答不合格、重复兑换——四种被拒完全不可区分。
     * 一旦某一种回不同的状态码或文案，探测者就能从差异里读出防护流程与判定顺序。
     */
    @Test
    void allRejectionsAreIndistinguishable() throws Exception {
        JsonObject badProofChallenge = challengeData(2, 2);
        JsonObject solved = challengeData(2, 2);

        Map<String, String> noChallenge = new HashMap<>();
        noChallenge.put("read", "16");
        noChallenge.put("write", "16");

        Map<String, String> unknownChallenge = new HashMap<>();
        unknownChallenge.put("challenge", "0123456789abcdef0123456789abcdef");
        unknownChallenge.put("proof", "12345");

        Map<String, String> badProof = new HashMap<>();
        badProof.put("challenge", badProofChallenge.get("challenge").getAsString());
        badProof.put("proof", "1");

        Map<String, String> valid = new HashMap<>();
        valid.put("challenge", solved.get("challenge").getAsString());
        valid.put("proof", solveChallenge(solved));

        Result first = runNonce(noChallenge);
        Result second = runNonce(unknownChallenge);
        Result third = runNonce(badProof);

        // 先正常兑换一次，再原样重放（题目已被消耗）
        assertEquals(200, runNonce(valid).status());
        Result replayed = runNonce(valid);

        for (Result result : new Result[]{second, third, replayed}) {
            assertEquals(first.status(), result.status(), "状态码应与缺挑战时一致");
            assertEquals(first.body(), result.body(), "响应体应与缺挑战时逐字节一致");
        }
    }

    @Test
    void exchangeIsRejectedWith429OnceTheIssueGateIsExhausted() throws Exception {
        JsonObject data = challengeData(1, 1);
        // 换题本身已经花掉一个令牌，这里把单来源闸门彻底打空
        for (int i = 0; i < ReplayIssueLimiter.PER_IP_REQUEST_BURST; i++) {
            ReplayIssueLimiter.tryAcquireRequest(IP);
        }
        assertFalse(ReplayIssueLimiter.tryAcquireRequest(IP), "闸门应已被打空");

        Map<String, String> params = new HashMap<>();
        params.put("challenge", data.get("challenge").getAsString());
        params.put("proof", solveChallenge(data));
        assertEquals(429, runNonce(params).status());
    }

    @Test
    void issuingFailsWith503WhenStorageIsDown() throws Exception {
        store.setDown(true);
        JsonObject data = challengeData(2, 0);
        Map<String, String> params = new HashMap<>();
        params.put("challenge", data.get("challenge").getAsString());
        params.put("proof", solveChallenge(data));
        Result result = runNonce(params);
        assertEquals(503, result.status());
        assertTrue(result.body().contains("success"));
    }

    /** 换一道题，返回响应里的 data 段。 */
    private JsonObject challengeData(int read, int write) throws Exception {
        return JsonParser.parseString(runChallenge(read, write).body()).getAsJsonObject()
                .getAsJsonObject("data");
    }

    /** 按公开契约独立解出该题的答案。 */
    private String solveChallenge(JsonObject data) {
        return ReplayChallengeSolver.solve(new ReplayChallengeService.Challenge(
                data.get("challenge").getAsString(),
                data.get("seed").getAsString(),
                data.get("difficulty").getAsInt(),
                data.get("read").getAsInt(),
                data.get("write").getAsInt(),
                null,
                null,
                0L));
    }

    private Result runChallenge(int read, int write) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("read", Integer.toString(read));
        params.put("write", Integer.toString(write));
        return run(new ReplayChallengeHandler(),
                (h, req, res) -> ((ReplayChallengeHandler) h).doGet(req, res), params);
    }

    private Result runNonce(Map<String, String> params) throws Exception {
        return run(new ReplayNonceHandler(),
                (h, req, res) -> ((ReplayNonceHandler) h).doGet(req, res), params);
    }

    /** 用动态代理桩执行一次 handler，返回状态码与响应体。 */
    private Result run(Object handler, HandlerCall call, Map<String, String> params) throws Exception {
        int[] status = {200};
        StringWriter body = new StringWriter();
        PrintWriter writer = new PrintWriter(body);
        HttpServletResponse response = proxy(HttpServletResponse.class, (p, m, a) -> {
            switch (m.getName()) {
                case "setStatus" -> status[0] = (int) a[0];
                case "getWriter" -> {
                    return writer;
                }
                default -> {
                }
            }
            return defaultValue(m);
        });
        HttpServletRequest request = proxy(HttpServletRequest.class, (p, m, a) -> switch (m.getName()) {
            case "getParameter" -> params.get((String) a[0]);
            case "getHeader" -> "User-Agent".equals(a[0]) ? UA : null;
            case "getRemoteAddr" -> IP;
            case "getRequestURI" -> "/api/replay/nonce";
            case "getContextPath" -> "";
            default -> defaultValue(m);
        });
        call.invoke(handler, request, response);
        writer.flush();
        return new Result(status[0], body.toString());
    }

    /** handler 的 GET 入口（同包内可直接调用）。 */
    private interface HandlerCall {
        void invoke(Object handler, HttpServletRequest request, HttpServletResponse response) throws Exception;
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

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
}
