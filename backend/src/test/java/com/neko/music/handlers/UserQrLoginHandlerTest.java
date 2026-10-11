package com.neko.music.handlers;

import com.neko.music.model.User;
import com.neko.music.service.QrLoginService;
import com.neko.music.service.UserAuthService;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 扫码登录一次性 token 的投递语义：同一个 sessionId 的**并发匿名读者**里只能有一个拿到 token，
 * 其余一律收到 {@code expired}。
 *
 * <p>回归背景：{@code load()} 与 {@code remove()} 曾拆成两步，多个并发 SSE 读者都读到同一份
 * {@code confirmed} 快照，于是各自把同一个 token 下发出去（实测 8 个读者里 2 个拿到同一 token）。
 * 这里故意让 {@code load()} 永远返回 confirmed 快照（比真实竞态更极端），从而确定性地覆盖该场景。</p>
 */
class UserQrLoginHandlerTest {

    private static final String SESSION_ID = "fVQ67uSEhtZBenMsDBCYpeFQVtnObnTC";
    private static final String TOKEN = "a9f128cf85bdb0739d0eae4cb84197b8676020c89aabcf83d2ec0d6a0164d1f7";
    private static final int USER_ID = 3482;

    /** 会话快照永远可读、投递权只发一次——等价于「所有读者都在删除前读到 confirmed」的最坏情况。 */
    private static final class StubQrLoginService extends QrLoginService {
        private final AtomicBoolean claimed = new AtomicBoolean();
        private final AtomicInteger removeCalls = new AtomicInteger();

        StubQrLoginService() {
            super(null, null);
        }

        @Override
        public Optional<Session> load(String sessionId) {
            Session session = new Session();
            session.status = STATUS_CONFIRMED;
            session.userId = USER_ID;
            session.token = TOKEN;
            return Optional.of(session);
        }

        @Override
        public boolean claimDelivery(String sessionId) {
            // 与 Redis SET NX 等价的一次性判定：并发读者里只有一个能 CAS 成功
            return claimed.compareAndSet(false, true);
        }

        @Override
        public void remove(String sessionId) {
            removeCalls.incrementAndGet();
        }
    }

    private static final class StubUserAuthService extends UserAuthService {
        StubUserAuthService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public Optional<User> findUserById(int userId) {
            return Optional.of(new User(userId, "test", null, "test@example.com",
                    true, null, "2026-01-01T00:00:00Z", null));
        }
    }

    private static final StubUserAuthService AUTH = new StubUserAuthService();

    private static final class Reader {
        private final StringWriter body = new StringWriter();
        private final PrintWriter writer = new PrintWriter(body);
        private final CountDownLatch done = new CountDownLatch(1);

        String body() {
            return body.toString();
        }
    }

    @Test
    void onlyOneConcurrentReaderReceivesTheOneTimeToken() throws Exception {
        StubQrLoginService service = new StubQrLoginService();
        int readers = 8;

        List<Reader> running = new ArrayList<>();
        for (int i = 0; i < readers; i++) {
            running.add(startReader(service));
        }
        for (Reader reader : running) {
            assertTrue(reader.done.await(10, TimeUnit.SECONDS), "读者未在超时内结束");
        }

        long delivered = running.stream().filter(reader -> reader.body().contains(TOKEN)).count();
        long expired = running.stream()
                .filter(reader -> reader.body().contains("\"status\":\"expired\"")).count();

        assertEquals(1, delivered, "同一个 token 只能被投递一次：" + bodies(running));
        assertEquals(readers - 1, expired, "其余读者必须收到 expired：" + bodies(running));
        assertEquals(1, service.removeCalls.get(), "只有投递者才该删除会话");
    }

    private static List<String> bodies(List<Reader> readers) {
        List<String> out = new ArrayList<>();
        for (Reader reader : readers) {
            out.add(reader.body().replace("\n", " ").trim());
        }
        return out;
    }

    /** 以匿名读者身份建立一次 SSE 状态流；返回的 Reader 在终止帧到达后 {@code done} 归零。 */
    private Reader startReader(QrLoginService service) throws Exception {
        Reader reader = new Reader();

        AsyncContext asyncContext = proxy(AsyncContext.class, (p, m, a) -> {
            if ("complete".equals(m.getName())) {
                reader.done.countDown();
            }
            return defaultValue(m);
        });

        HttpServletRequest request = proxy(HttpServletRequest.class, (p, m, a) -> switch (m.getName()) {
            case "getPathInfo" -> "/status";
            case "getParameter" -> SESSION_ID;
            case "startAsync" -> asyncContext;
            case "getMethod" -> "GET";
            default -> defaultValue(m);
        });

        HttpServletResponse response = proxy(HttpServletResponse.class, (p, m, a) -> switch (m.getName()) {
            case "getWriter" -> reader.writer;
            default -> defaultValue(m);
        });

        UserQrLoginHandler handler = new UserQrLoginHandler();
        inject(handler, "qrLoginService", service);
        inject(handler, "userAuthService", AUTH);

        Thread thread = new Thread(() -> {
            try {
                handler.doGet(request, response);
            } catch (Exception e) {
                reader.done.countDown();
            }
        });
        thread.start();
        return reader;
    }

    private static void inject(Object target, String field, Object value) throws Exception {
        var declared = UserQrLoginHandler.class.getDeclaredField(field);
        declared.setAccessible(true);
        declared.set(target, value);
    }

    @SuppressWarnings("unchecked")
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
