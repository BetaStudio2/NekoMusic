package com.neko.music.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 认证入口来源限流测试：固定窗口内突发额度用尽即拒、窗口过期后恢复、不同类别与不同来源互不牵连。
 *
 * <p>生产计数落在 Redis，这里注入纯内存的 {@link AuthAbuseLimiter.Store} 并用可控时钟推进窗口，
 * 因此用例不依赖真实 Redis。</p>
 */
class AuthAbuseLimiterTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private AuthAbuseLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new AuthAbuseLimiter(new FakeStore(now));
    }

    private void exhaust(AuthAbuseLimiter.Scope scope, String ip) {
        for (int i = 0; i < scope.maxRequests; i++) {
            assertTrue(limiter.tryAcquire(scope, ip).isEmpty(), "第 " + i + " 次应在窗口额度内");
        }
    }

    @Test
    void windowQuotaIsExhaustedThenRejected() {
        exhaust(AuthAbuseLimiter.Scope.LOGIN, "1.2.3.4");

        Optional<Long> denied = limiter.tryAcquire(AuthAbuseLimiter.Scope.LOGIN, "1.2.3.4");
        assertTrue(denied.isPresent(), "窗口额度用尽后必须拒绝");
        assertTrue(denied.get() >= 1, "Retry-After 至少为 1 秒");
    }

    @Test
    void otherSourcesAreNotAffected() {
        exhaust(AuthAbuseLimiter.Scope.LOGIN, "1.2.3.4");
        assertTrue(limiter.tryAcquire(AuthAbuseLimiter.Scope.LOGIN, "5.6.7.8").isEmpty(),
                "限流按来源隔离，不能牵连其它 IP");
    }

    @Test
    void scopesAreCountedIndependently() {
        exhaust(AuthAbuseLimiter.Scope.REGISTER, "1.2.3.4");
        assertTrue(limiter.tryAcquire(AuthAbuseLimiter.Scope.REGISTER, "1.2.3.4").isPresent());
        assertTrue(limiter.tryAcquire(AuthAbuseLimiter.Scope.LOGIN, "1.2.3.4").isEmpty(),
                "注册被限不应牵连登录");
    }

    @Test
    void quotaRecoversAfterWindowExpires() {
        exhaust(AuthAbuseLimiter.Scope.LOGIN, "1.2.3.4");
        assertTrue(limiter.tryAcquire(AuthAbuseLimiter.Scope.LOGIN, "1.2.3.4").isPresent(),
                "窗口内额度用尽仍应拒绝");
        assertTrue(limiter.tryAcquire(AuthAbuseLimiter.Scope.LOGIN, "1.2.3.4").isPresent(),
                "刚拒绝后立即重试仍在冷却中");

        now.addAndGet(AuthAbuseLimiter.Scope.LOGIN.windowSeconds * 1000L + 1);
        assertTrue(limiter.tryAcquire(AuthAbuseLimiter.Scope.LOGIN, "1.2.3.4").isEmpty(),
                "窗口过期后应重新获得完整额度");
    }

    @Test
    void blankSourceStillCounted() {
        exhaust(AuthAbuseLimiter.Scope.CAPTCHA, null);
        assertTrue(limiter.tryAcquire(AuthAbuseLimiter.Scope.CAPTCHA, "  ").isPresent(),
                "缺失来源不得成为无限额度旁路");
    }

    @Test
    void storeFailureFailsOpen() {
        AuthAbuseLimiter open = new AuthAbuseLimiter(new AuthAbuseLimiter.Store() {
            @Override
            public long incrementInWindow(String key, int windowSeconds) {
                return -1L;
            }

            @Override
            public long ttlSeconds(String key) {
                return 0L;
            }
        });
        for (int i = 0; i < 100; i++) {
            assertTrue(open.tryAcquire(AuthAbuseLimiter.Scope.LOGIN, "1.2.3.4").isEmpty(),
                    "计数存储不可用时应放行，不应把登录链路打死");
        }
    }

    /** 固定窗口内存实现：首次写入建立窗口，窗口内自增，过期后重置。 */
    private static final class FakeStore implements AuthAbuseLimiter.Store {

        private final Map<String, long[]> windows = new HashMap<>();
        private final AtomicLong now;

        FakeStore(AtomicLong now) {
            this.now = now;
        }

        @Override
        public long incrementInWindow(String key, int windowSeconds) {
            long t = now.get();
            long[] entry = windows.get(key);
            if (entry == null || entry[1] <= t) {
                entry = new long[]{1L, t + windowSeconds * 1000L};
                windows.put(key, entry);
            } else {
                entry[0]++;
            }
            return entry[0];
        }

        @Override
        public long ttlSeconds(String key) {
            long[] entry = windows.get(key);
            if (entry == null) {
                return 0;
            }
            long remain = (entry[1] - now.get()) / 1000L;
            return remain > 0 ? remain : 0;
        }
    }
}
