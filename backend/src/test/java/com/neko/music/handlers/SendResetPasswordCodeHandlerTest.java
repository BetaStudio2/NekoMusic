package com.neko.music.handlers;

import com.neko.music.service.AuthAbuseLimiter;
import com.neko.music.service.UserAuthService;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 重置密码发码接口的账号枚举防护测试：邮箱注册与否必须给出完全一致的响应
 * （状态码 / 结构 / 文案 / 是否限流），只有「是否真的发信」可以不同。
 *
 * <p>用动态代理桩替代 Servlet 容器与用户服务，不依赖 MySQL / Redis / SMTP。</p>
 */
class SendResetPasswordCodeHandlerTest {

    private static final String CLIENT_IP = "203.0.113.7";

    @BeforeEach
    void resetAbuseLimiter() {
        // AuthAbuseLimiter 是进程内共享状态：本测试会多次调用发码接口，先清空避免用例相互影响
        AuthAbuseLimiter.reset();
    }

    /** 只覆盖与账号存在性 / 发信 / 限流相关的方法，其余不会被调用。 */
    private static final class StubAuthService extends UserAuthService {
        boolean exists;
        Optional<Long> cooldown = Optional.empty();
        boolean sent;
        boolean sendThrows;
        String sentEmail;
        String throttledIp;

        StubAuthService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public boolean userExistsByEmail(String email) {
            return exists;
        }

        @Override
        public void sendResetPasswordCodeAsync(String email, String nickname) {
            if (sendThrows) {
                throw new IllegalStateException("发信线程提交失败");
            }
            sent = true;
            sentEmail = email;
        }

        @Override
        public Optional<Long> acquireResetCodeSlot(String email, String clientIp) {
            throttledIp = clientIp;
            return cooldown;
        }
    }

    private record Outcome(int status, boolean sent, String body, String retryAfter, String throttledIp) {
    }

    private Outcome run(String requestBody, StubAuthService auth) throws Exception {
        SendResetPasswordCodeHandler handler = new SendResetPasswordCodeHandler();
        var field = SendResetPasswordCodeHandler.class.getDeclaredField("userAuthService");
        field.setAccessible(true);
        field.set(handler, auth);

        int[] status = {200};
        Map<String, String> responseHeaders = new HashMap<>();
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

        byte[] payload = requestBody.getBytes(StandardCharsets.UTF_8);
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(payload), StandardCharsets.UTF_8));
        HttpServletRequest request = proxy(HttpServletRequest.class, (p, m, a) -> switch (m.getName()) {
            case "getReader" -> reader;
            case "getRemoteAddr" -> CLIENT_IP;
            case "getMethod" -> "POST";
            case "getRequestURI" -> "/api/user/send-reset-code";
            case "getContextPath" -> "";
            case "getHeader" -> null;
            case "getInputStream" -> emptyStream();
            default -> defaultValue(m);
        });

        handler.doPost(request, response);

        return new Outcome(status[0], auth.sent, body.toString(),
                responseHeaders.get("retry-after"), auth.throttledIp);
    }

    @Test
    void registeredAndUnregisteredEmailsLookIdentical() throws Exception {
        StubAuthService unregistered = new StubAuthService();
        unregistered.exists = false;
        Outcome unknown = run("{\"email\":\"nobody@example.com\"}", unregistered);

        StubAuthService registered = new StubAuthService();
        registered.exists = true;
        Outcome known = run("{\"email\":\"someone@example.com\"}", registered);

        // 状态码、文案、结构完全一致：外部无法据此区分账号是否存在
        assertEquals(200, unknown.status());
        assertEquals(known.status(), unknown.status());
        assertEquals(known.body(), unknown.body());
        assertTrue(unknown.body().contains(SendResetPasswordCodeHandler.UNIFORM_MESSAGE), unknown.body());
        assertTrue(unknown.body().contains("\"success\":true"), unknown.body());

        // 唯一允许的差异：只有已注册邮箱真的发信
        assertFalse(unknown.sent());
        assertTrue(known.sent());
        assertEquals("someone@example.com", registered.sentEmail);

        // 限流用的来源 IP 取自可信解析，两条分支一致
        assertEquals(CLIENT_IP, unknown.throttledIp());
        assertEquals(CLIENT_IP, known.throttledIp());
    }

    @Test
    void rateLimitAppliesBeforeExistenceCheckAndLooksIdentical() throws Exception {
        StubAuthService unregistered = new StubAuthService();
        unregistered.exists = false;
        unregistered.cooldown = Optional.of(30L);
        Outcome unknown = run("{\"email\":\"nobody@example.com\"}", unregistered);

        StubAuthService registered = new StubAuthService();
        registered.exists = true;
        registered.cooldown = Optional.of(30L);
        Outcome known = run("{\"email\":\"someone@example.com\"}", registered);

        // 429 对两种邮箱完全一样，且都不会发信：限流本身不能成为存在性信号
        assertEquals(429, unknown.status());
        assertEquals(known.status(), unknown.status());
        assertEquals(known.body(), unknown.body());
        assertEquals("30", unknown.retryAfter());
        assertEquals("30", known.retryAfter());
        assertFalse(unknown.sent());
        assertFalse(known.sent());
    }

    @Test
    void invalidInputStillFailsWithoutRevealingAnything() throws Exception {
        StubAuthService auth = new StubAuthService();
        Outcome blank = run("{\"email\":\"  \"}", auth);
        Outcome malformed = run("{\"email\":\"not-an-email\"}", auth);

        assertEquals(200, blank.status());
        assertTrue(blank.body().contains("邮箱不能为空"), blank.body());
        assertTrue(malformed.body().contains("邮箱格式不正确"), malformed.body());
        assertFalse(auth.sent);
    }

    @Test
    void sendFailureIsNotObservableToCaller() throws Exception {
        // 已注册但发信提交失败（例如线程池拒绝）时，对外仍是同一份中性响应
        StubAuthService auth = new StubAuthService();
        auth.exists = true;
        auth.sendThrows = true;
        Outcome outcome = run("{\"email\":\"someone@example.com\"}", auth);

        // 处理器只负责把发信交给后台线程，失败在服务端记录；响应不受影响
        assertEquals(200, outcome.status());
        assertFalse(auth.sent);
        assertTrue(outcome.body().contains(SendResetPasswordCodeHandler.UNIFORM_MESSAGE), outcome.body());
    }

    private static ServletInputStream emptyStream() {
        return new ServletInputStream() {
            @Override
            public int read() {
                return -1;
            }

            @Override
            public boolean isFinished() {
                return true;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    /** 代理的默认返回值：基本类型给零值，其余给 null。 */
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
