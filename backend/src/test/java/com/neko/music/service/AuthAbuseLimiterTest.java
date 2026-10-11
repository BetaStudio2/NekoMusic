package com.neko.music.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 认证入口来源限流测试：突发额度用尽即拒、按时间补充、不同类别与不同来源互不牵连。
 */
class AuthAbuseLimiterTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);

    @BeforeEach
    void setUp() {
        AuthAbuseLimiter.reset();
        AuthAbuseLimiter.useClock(now::get);
    }

    @AfterEach
    void tearDown() {
        AuthAbuseLimiter.useClock(null);
        AuthAbuseLimiter.reset();
    }

    private void exhaust(AuthAbuseLimiter.Scope scope, String ip) {
        for (int i = 0; i < scope.burst; i++) {
            assertTrue(AuthAbuseLimiter.tryAcquire(scope, ip).isEmpty(), "第 " + i + " 次应在突发额度内");
        }
    }

    @Test
    void burstIsExhaustedThenRejected() {
        exhaust(AuthAbuseLimiter.Scope.LOGIN, "1.2.3.4");

        Optional<Long> denied = AuthAbuseLimiter.tryAcquire(AuthAbuseLimiter.Scope.LOGIN, "1.2.3.4");
        assertTrue(denied.isPresent(), "突发额度用尽后必须拒绝");
        assertTrue(denied.get() >= 1, "Retry-After 至少为 1 秒");
    }

    @Test
    void otherSourcesAreNotAffected() {
        exhaust(AuthAbuseLimiter.Scope.LOGIN, "1.2.3.4");
        assertTrue(AuthAbuseLimiter.tryAcquire(AuthAbuseLimiter.Scope.LOGIN, "5.6.7.8").isEmpty(),
                "限流按来源隔离，不能牵连其它 IP");
    }

    @Test
    void scopesAreCountedIndependently() {
        exhaust(AuthAbuseLimiter.Scope.REGISTER, "1.2.3.4");
        assertTrue(AuthAbuseLimiter.tryAcquire(AuthAbuseLimiter.Scope.REGISTER, "1.2.3.4").isPresent());
        assertTrue(AuthAbuseLimiter.tryAcquire(AuthAbuseLimiter.Scope.LOGIN, "1.2.3.4").isEmpty(),
                "注册被限不应牵连登录");
    }

    @Test
    void tokensRefillOverTime() {
        exhaust(AuthAbuseLimiter.Scope.LOGIN, "1.2.3.4");
        assertTrue(AuthAbuseLimiter.tryAcquire(AuthAbuseLimiter.Scope.LOGIN, "1.2.3.4").isPresent());
        assertFalse(AuthAbuseLimiter.tryAcquire(AuthAbuseLimiter.Scope.LOGIN, "1.2.3.4").isEmpty(),
                "刚拒绝后立即重试仍应在冷却中");

        // 登录速率 20/分钟：等 6 秒应补回两个令牌（取整余量，避开浮点边界）
        now.addAndGet(6_000L);
        assertTrue(AuthAbuseLimiter.tryAcquire(AuthAbuseLimiter.Scope.LOGIN, "1.2.3.4").isEmpty(),
                "冷却期过后应按速率补充令牌");
    }

    @Test
    void blankSourceStillCounted() {
        exhaust(AuthAbuseLimiter.Scope.CAPTCHA, null);
        assertTrue(AuthAbuseLimiter.tryAcquire(AuthAbuseLimiter.Scope.CAPTCHA, "  ").isPresent(),
                "缺失来源不得成为无限额度旁路");
    }
}
