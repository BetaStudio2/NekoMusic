package com.neko.music.handlers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.neko.music.service.ReplayChallengeService;
import com.neko.music.service.ReplayDifficultyPolicy;
import com.neko.music.service.ReplayIssueLimiter;
import com.neko.music.service.ReplaySourceMarkService;
import com.neko.music.service.TestReplayPressureStore;
import com.neko.music.service.TestReplaySourceMarkStore;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 换题接口的行为测试：加压后的难度、越界（题目已不可解）时落库标记并拒绝、已标记来源不再发题，
 * 以及正常来源完全不触达标记表。
 *
 * <p>用动态代理桩替代 Servlet 容器，不依赖 Redis 与 MySQL。</p>
 */
class ReplayChallengeHandlerTest {

    private static final String IP = "127.0.0.1";
    private static final String UA = "NekoMusic-android/202601008";
    private static final long FROZEN_MILLIS = 1_700_000_000_000L;
    /** 正常来源的量级（远低于基准线）与明显加压的量级。 */
    private static final long NORMAL_REQUESTS = 64L;
    private static final long PRESSURED_REQUESTS = 1_000_000L;

    private final TestReplayPressureStore pressure = new TestReplayPressureStore();
    private final TestReplaySourceMarkStore marks = new TestReplaySourceMarkStore();

    @BeforeEach
    void setUp() {
        ReplayIssueLimiter.reset();
        ReplayIssueLimiter.useClock(() -> FROZEN_MILLIS);
        ReplayChallengeService.reset();
        ReplayDifficultyPolicy.useStore(pressure);
        ReplaySourceMarkService.setCurrent(new ReplaySourceMarkService(marks));
    }

    @AfterEach
    void tearDown() {
        ReplayDifficultyPolicy.reset();
        ReplaySourceMarkService.setCurrent(null);
        ReplayIssueLimiter.useClock(null);
    }

    @Test
    void normalSourceGetsBaseDifficultyAndNeverTouchesTheMarkTable() throws Exception {
        pressure.seed(IP, NORMAL_REQUESTS, 0);

        Result result = runChallenge(16, 16);
        assertEquals(200, result.status());
        JsonObject data = JsonParser.parseString(result.body()).getAsJsonObject().getAsJsonObject("data");
        assertEquals(ReplayChallengeService.targetDifficulty(16, 16, 0), data.get("difficulty").getAsInt());
        assertEquals(0, marks.reads(), "正常来源不该为标记判定回源查库");
    }

    @Test
    void pressuredSourcePaysMoreButStillGetsAChallenge() throws Exception {
        pressure.seed(IP, PRESSURED_REQUESTS, 0);
        int expectedBits = ReplayDifficultyPolicy.rateBits(PRESSURED_REQUESTS);

        Result result = runChallenge(16, 16);
        assertEquals(200, result.status());
        JsonObject data = JsonParser.parseString(result.body()).getAsJsonObject().getAsJsonObject("data");
        assertEquals(ReplayChallengeService.targetDifficulty(16, 16, expectedBits),
                data.get("difficulty").getAsInt());
        assertTrue(expectedBits > 0);
        assertEquals(0, marks.upserts().size(), "仍在可发题范围内，不该落库标记");
    }

    @Test
    void crossingTheIssuableLineMarksTheSourceAndRejects() throws Exception {
        // 频率把目标难度推到可发题界线之上：题目对任何客户端都已不可解
        pressure.seed(IP, Long.MAX_VALUE / 4, 0);

        Result result = runChallenge(16, 16);
        assertEquals(429, result.status());
        assertTrue(result.body().contains("false"));
        assertEquals(1, marks.upserts().size(), "越界应向主库落一条标记");
        assertTrue(marks.upserts().get(0).contains(ReplaySourceMarkService.REASON_UNSOLVABLE));
        assertTrue(marks.upserts().get(0).startsWith(IP + ":"));
    }

    @Test
    void markedSourceIsRejectedWhileItStaysPressured() throws Exception {
        marks.seedMarked(IP);
        pressure.seed(IP, PRESSURED_REQUESTS, 0);

        assertEquals(429, runChallenge(16, 16).status());
    }

    @Test
    void markedSourceThatStopsPressingGetsServedAgain() throws Exception {
        // 标记是临时处置而不是永久黑名单：来源回到正常速率后重新可领取
        marks.seedMarked(IP);
        pressure.seed(IP, 0, 0);

        assertEquals(200, runChallenge(16, 16).status());
    }

    private record Result(int status, String body) {
    }

    private Result runChallenge(int read, int write) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("read", Integer.toString(read));
        params.put("write", Integer.toString(write));
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
            case "getRequestURI" -> "/api/replay/challenge";
            case "getContextPath" -> "";
            default -> defaultValue(m);
        });
        new ReplayChallengeHandler().doGet(request, response);
        writer.flush();
        return new Result(status[0], body.toString());
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
