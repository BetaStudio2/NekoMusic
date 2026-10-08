package com.neko.music.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 签发限额测试：单来源有突发上限、令牌按时间补充、全站 nonce 额度是硬顶。
 */
class ReplayIssueLimiterTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);

    @BeforeEach
    void setUp() {
        ReplayIssueLimiter.reset();
        ReplayIssueLimiter.useClock(now::get);
    }

    @AfterEach
    void tearDown() {
        ReplayIssueLimiter.useClock(null);
        ReplayIssueLimiter.reset();
    }

    @Test
    void perSourceBurstIsLimitedThenRefillsOverTime() {
        for (int i = 0; i < ReplayIssueLimiter.PER_IP_REQUEST_BURST; i++) {
            assertTrue(ReplayIssueLimiter.tryAcquireRequest("1.2.3.4"), "第 " + i + " 次应在突发额度内");
        }
        assertFalse(ReplayIssueLimiter.tryAcquireRequest("1.2.3.4"), "突发额度用尽后必须拒绝");

        // 单来源被限不影响其它来源
        assertTrue(ReplayIssueLimiter.tryAcquireRequest("5.6.7.8"));

        // 一秒后按速率补充
        now.addAndGet(1000L);
        for (int i = 0; i < ReplayIssueLimiter.PER_IP_REQUESTS_PER_SECOND; i++) {
            assertTrue(ReplayIssueLimiter.tryAcquireRequest("1.2.3.4"));
        }
        assertFalse(ReplayIssueLimiter.tryAcquireRequest("1.2.3.4"));
    }

    @Test
    void globalNonceBudgetCapsTotalIssuance() {
        assertTrue(ReplayIssueLimiter.tryAcquireNonces(ReplayIssueLimiter.GLOBAL_NONCE_BURST));
        assertFalse(ReplayIssueLimiter.tryAcquireNonces(1), "全站额度用尽后必须拒绝");

        now.addAndGet(1000L);
        assertTrue(ReplayIssueLimiter.tryAcquireNonces(ReplayIssueLimiter.GLOBAL_NONCES_PER_SECOND));
        assertFalse(ReplayIssueLimiter.tryAcquireNonces(ReplayIssueLimiter.GLOBAL_NONCES_PER_SECOND));
    }

    @Test
    void nonPositiveNonceRequestIsNoop() {
        assertTrue(ReplayIssueLimiter.tryAcquireNonces(0));
        assertTrue(ReplayIssueLimiter.tryAcquireNonces(-5));
    }
}
