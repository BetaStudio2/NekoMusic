package com.neko.music.handlers;

import com.neko.music.model.Admin;
import com.neko.music.service.AdminAuthService;
import com.neko.music.service.AdminLoginThrottleService;
import com.neko.music.service.TestAdminLoginThrottleStore;
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
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理端登录的枚举防护测试：账号存在与否，只能影响「登录是否成功」，不能影响任何其它可观测差异
 * ——包括失败响应、限流计数与限流后的 429。
 *
 * <p>用动态代理桩替代 Servlet 容器，用内存 store 替代 Redis，不依赖 MySQL / Redis。</p>
 */
class AdminLoginHandlerTest {

    private static final String CLIENT_IP = "203.0.113.7";
    private static final String UNKNOWN_USER = "no_such_user_zzz";
    private static final String KNOWN_USER = "Admin";
    private static final String CORRECT_PASSWORD = "correct-horse";

    /**
     * 认证桩：只有 {@link #KNOWN_USER} + {@link #CORRECT_PASSWORD} 才成功，其余一律失败。
     *
     * <p>刻意不做「按用户名存在性」的可变开关——「账号存在但口令错」与「账号不存在」在真实实现里
     * 都是 401，桩也必须一样；无状态也让测试可以与其它用例并行跑。</p>
     *
     * <p>按真实实现的两段式接口覆写：{@link #findByUsername} 走「数据库排序规则」口径
     * （{@link #foldLikeDatabase} 近似 {@code utf8mb4_uca1400_ai_ci}：忽略大小写、Unicode 兼容折叠
     * 与重音），{@link #authenticateResolved} 对已解析账号做口令校验。这样大小写 / 全角 / 重音等
     * 变体会解析到同一账号 ID，从而共享同一限流桶。</p>
     */
    private static final class StubAdminAuthService extends AdminAuthService {
        final AtomicInteger authenticateCalls = new AtomicInteger();

        StubAdminAuthService() {
            super(null, null);
        }

        @Override
        public Optional<Admin> findByUsername(String username) {
            if (username == null
                    || !foldLikeDatabase(KNOWN_USER).equals(foldLikeDatabase(username.trim()))) {
                return Optional.empty();
            }
            return Optional.of(new Admin(1, username, "hash", "admin@nekomusic.com",
                    true, "super_admin", 0L, 0L));
        }

        /**
         * 近似生产库 {@code utf8mb4_uca1400_ai_ci} 的用户名比较口径：NFKD 兼容分解、去掉组合记号
         * （忽略重音）、再转小写。仅用于让桩与真实数据库的折叠行为一致，独立于被测实现。
         */
        private static String foldLikeDatabase(String value) {
            String decomposed = Normalizer.normalize(value, Normalizer.Form.NFKD);
            StringBuilder out = new StringBuilder(decomposed.length());
            decomposed.codePoints().forEach(codePoint -> {
                int type = Character.getType(codePoint);
                if (type != Character.NON_SPACING_MARK
                        && type != Character.COMBINING_SPACING_MARK
                        && type != Character.ENCLOSING_MARK) {
                    out.appendCodePoint(codePoint);
                }
            });
            return out.toString().toLowerCase(Locale.ROOT);
        }

        @Override
        public Optional<Admin> authenticateResolved(Optional<Admin> resolved, String password) {
            authenticateCalls.incrementAndGet();
            if (resolved.isEmpty() || !CORRECT_PASSWORD.equals(password)) {
                return Optional.empty();
            }
            return resolved;
        }

        @Override
        public String createAdminSession(Admin admin) {
            return "session-token";
        }
    }

    /** 构造一次即可：父类构造会生成 Argon2 陪跑哈希，所有用例复用同一实例以免重复付费。 */
    private static final StubAdminAuthService AUTH = new StubAdminAuthService();

    private TestAdminLoginThrottleStore store;
    private AdminLoginThrottleService throttle;

    @BeforeEach
    void setUp() {
        AUTH.authenticateCalls.set(0);
        store = new TestAdminLoginThrottleStore();
        throttle = new AdminLoginThrottleService(store);
    }

    private record Outcome(int status, String body, String retryAfter, String token) {
    }

    private Outcome login(String bodyJson, String method) throws Exception {
        AdminLoginHandler handler = new AdminLoginHandler();
        inject(handler, "adminAuthService", AUTH);
        inject(handler, "throttleService", throttle);

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

        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(bodyJson.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
        HttpServletRequest request = proxy(HttpServletRequest.class, (p, m, a) -> switch (m.getName()) {
            case "getReader" -> reader;
            case "getRemoteAddr" -> CLIENT_IP;
            case "getMethod" -> method;
            case "getRequestURI" -> "/api/admin/login";
            case "getContextPath" -> "";
            case "getHeader" -> null;
            case "getInputStream" -> emptyStream();
            default -> defaultValue(m);
        });

        handler.doPost(request, response);

        String text = body.toString();
        String token = text.contains("session-token") ? "session-token" : null;
        return new Outcome(status[0], text, headers.get("retry-after"), token);
    }

    private void inject(Object target, String field, Object value) throws Exception {
        var declared = AdminLoginHandler.class.getDeclaredField(field);
        declared.setAccessible(true);
        declared.set(target, value);
    }

    private static String credentials(String username) {
        return credentials(username, "guess");
    }

    private static String credentials(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void unknownAndKnownUsernamesGetIdentical401() throws Exception {
        // 前者用户不存在，后者用户存在但口令错——真实实现里两者都是同一条 401
        Outcome unknown = login(credentials(UNKNOWN_USER), "POST");
        Outcome known = login(credentials(KNOWN_USER), "POST");

        assertEquals(401, unknown.status());
        assertEquals(unknown.status(), known.status());
        assertEquals(unknown.body(), known.body(), "存在的用户名与不存在的用户名必须返回完全一致的 401");
        assertTrue(unknown.body().contains("用户名或密码错误"), unknown.body());
        assertEquals(unknown.retryAfter(), known.retryAfter());
        assertFalse(unknown.body().contains("session-token"));
    }

    @Test
    void failedAttemptsAreCountedForUnknownUsernamesToo() throws Exception {
        for (int i = 0; i < 3; i++) {
            login(credentials(UNKNOWN_USER), "POST");
        }
        assertEquals(3, AUTH.authenticateCalls.get());

        // 不存在的用户名同样产生失败计数（用户名 + 来源各一个键），因此锁定不是存在性信号
        assertEquals(2, store.size());
    }

    @Test
    void lockoutResponseIsIdenticalForKnownAndUnknownUsernames() throws Exception {
        Outcome unknownLocked = lockOutWith(UNKNOWN_USER);

        // 重置计数，避免两段共享同一来源维度的计数而互相干扰
        store = new TestAdminLoginThrottleStore();
        throttle = new AdminLoginThrottleService(store);
        Outcome knownLocked = lockOutWith(KNOWN_USER);

        assertEquals(429, unknownLocked.status());
        assertEquals(unknownLocked.status(), knownLocked.status());
        assertEquals(unknownLocked.body(), knownLocked.body(), "锁定后的响应不能区分账号是否存在");
        assertEquals(unknownLocked.retryAfter(), knownLocked.retryAfter());

        // 只锁了这两个用户名，来源维度（10 次 < 阈值）与其它用户名都没被牵连
        assertTrue(throttle.beginAttempt("admin_backup", CLIENT_IP).isEmpty());
    }

    /** 用指定用户名连续失败到阈值，再返回「已锁定」那一次的结果。 */
    private Outcome lockOutWith(String username) throws Exception {
        for (int i = 0; i < AdminLoginThrottleService.MAX_FAILURES_PER_USERNAME; i++) {
            login(credentials(username), "POST");
        }
        return login(credentials(username), "POST");
    }

    @Test
    void caseVariantsShareTheSameLockoutBucket() throws Exception {
        // 账号查找大小写不敏感（admin / Admin / ADMIN 是同一个账号），锁定桶必须一致：
        // 否则攻击者用大小写变体各开一个空桶，就能把 10 次的阈值放大成 10 × 变体数
        Outcome locked = lockOutWith("admin");
        assertEquals(429, locked.status());

        for (String variant : new String[]{"Admin", "ADMIN", "aDmIn"}) {
            Outcome outcome = login(credentials(variant), "POST");
            assertEquals(429, outcome.status(), "大小写变体必须共享同一锁定桶: " + variant);
            assertEquals(locked.body(), outcome.body(), "锁定响应应完全一致: " + variant);
            assertEquals(locked.retryAfter(), outcome.retryAfter());
        }
    }

    @Test
    void accentVariantsShareTheSameLockoutBucket() throws Exception {
        // 生产库按 utf8mb4_uca1400_ai_ci 比较用户名（忽略重音）：admín / ádmin 等都命中同一行 admin。
        // 账号维度按解析出的管理员 ID 分桶后，这些变体与 admin 共享同一锁定桶；若退回「提交字符串」
        // 分桶，每个重音变体都会是一个全新的 10 次空桶，把每账号配额放大 10 × 变体数（CWE-178）。
        Outcome locked = lockOutWith("admin");
        assertEquals(429, locked.status(), locked.body());

        for (String variant : new String[]{"admín", "ádmin", "admìn", "admîn", "admïn", "admĩn"}) {
            Outcome outcome = login(credentials(variant), "POST");
            assertEquals(429, outcome.status(), "重音变体必须共享同一锁定桶: " + variant);
            assertEquals(locked.body(), outcome.body(), "锁定响应应完全一致: " + variant);
            assertEquals(locked.retryAfter(), outcome.retryAfter());
        }

        // 反向对照：真正不存在的账号仍各自计数（不被 admin 的锁定状态牵连）
        assertEquals(401, login(credentials(UNKNOWN_USER), "POST").status());
    }

    @Test
    void variantIsCountedUnderTheResolvedAccountIdBucketNotTheSubmittedString() throws Exception {
        // 结构性不变量（本 finding 的根因）：计数键必须来自解析出的账号身份，而不是提交字符串。
        // 按字符串分桶时，'admín' 会另开一个 in:admín 空桶，每账号 10 次/900s 被放大成 10 × 变体数。
        login(credentials("admín"), "POST");

        Set<String> keys = store.keys();
        assertTrue(keys.stream().anyMatch(key -> key.contains("user:id:1")),
                "重音变体应按解析出的管理员 ID 计数，实际键: " + keys);
        assertFalse(keys.stream().anyMatch(key -> key.contains("in:adm")),
                "不得按提交字符串另开一个桶，实际键: " + keys);
    }

    @Test
    void successfulAttemptDoesNotConsumeFailureBudget() throws Exception {
        // 成功登录会退回本次尝试占用的配额：正常管理员反复登录不会把自己锁死
        for (int i = 0; i < AdminLoginThrottleService.MAX_FAILURES_PER_SOURCE + 1; i++) {
            assertEquals(200, login(credentials(KNOWN_USER, CORRECT_PASSWORD), "POST").status());
        }
        assertEquals(200, login(credentials(KNOWN_USER, CORRECT_PASSWORD), "POST").status());
    }

    @Test
    void repeatedFailuresFromOneSourceLockThatSourceWhateverTheUsername() throws Exception {
        for (int i = 0; i < AdminLoginThrottleService.MAX_FAILURES_PER_SOURCE; i++) {
            login(credentials("random_user_" + i), "POST");
        }

        Outcome blocked = login(credentials(KNOWN_USER), "POST");
        assertEquals(429, blocked.status(), "同一来源的失败累计应锁定该来源");
        assertTrue(blocked.retryAfter() != null);
    }

    @Test
    void successfulLoginClearsUsernameFailuresAndReturnsToken() throws Exception {
        login(credentials(KNOWN_USER), "POST");
        int keysAfterFailure = store.size();

        Outcome success = login(credentials(KNOWN_USER, CORRECT_PASSWORD), "POST");

        assertEquals(200, success.status());
        assertTrue(success.body().contains("\"success\":true"), success.body());
        assertEquals("session-token", success.token());
        assertTrue(store.size() < keysAfterFailure, "登录成功应清掉该用户名的失败计数");
    }

    @Test
    void blankCredentialsAreRejectedWithoutTouchingTheCounters() throws Exception {
        Outcome blank = login("{\"username\":\"  \",\"password\":\"x\"}", "POST");
        Outcome missing = login("{}", "POST");

        assertEquals(400, blank.status());
        assertEquals(400, missing.status());
        assertEquals(0, store.size(), "参数不合法不应计入失败次数");
        assertEquals(0, AUTH.authenticateCalls.get());
    }

    @Test
    void optionsPreflightIsUntouched() throws Exception {
        Outcome preflight = login("", "OPTIONS");
        assertEquals(200, preflight.status());
        assertEquals(0, store.size());
        assertEquals(0, AUTH.authenticateCalls.get());
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
