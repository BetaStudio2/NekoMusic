package com.neko.music.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 外站补全来源配额：窗口内额度用尽即跳过补全、不同来源隔离、窗口过期恢复、存储不可用放行。
 *
 * <p>生产计数落在 Redis，这里注入纯内存的 {@link AuthAbuseLimiter.Store} 并用可控时钟推进窗口，
 * 因此用例不依赖真实 Redis。</p>
 */
class ExternalFillQuotaServiceTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private ExternalFillQuotaService quota;

    @BeforeEach
    void setUp() {
        quota = new ExternalFillQuotaService(new FakeStore(now));
    }

    @Test
    void quotaIsExhaustedThenDenied() {
        for (int i = 0; i < ExternalFillQuotaService.MAX_FILLS_PER_WINDOW; i++) {
            assertTrue(quota.tryAcquire("1.2.3.4"), "第 " + i + " 次应在配额内");
        }
        assertFalse(quota.tryAcquire("1.2.3.4"), "窗口额度用尽后必须跳过补全");
    }

    @Test
    void otherSourcesAreNotAffected() {
        for (int i = 0; i < ExternalFillQuotaService.MAX_FILLS_PER_WINDOW; i++) {
            quota.tryAcquire("1.2.3.4");
        }
        assertFalse(quota.tryAcquire("1.2.3.4"));
        assertTrue(quota.tryAcquire("5.6.7.8"), "配额按来源隔离，不能牵连其它 IP");
    }

    @Test
    void quotaRecoversAfterWindowExpires() {
        for (int i = 0; i < ExternalFillQuotaService.MAX_FILLS_PER_WINDOW; i++) {
            quota.tryAcquire("1.2.3.4");
        }
        assertFalse(quota.tryAcquire("1.2.3.4"));

        now.addAndGet(ExternalFillQuotaService.WINDOW_SECONDS * 1000L + 1);
        assertTrue(quota.tryAcquire("1.2.3.4"), "窗口过期后应重新获得完整额度");
    }

    @Test
    void blankSourceStillCounted() {
        for (int i = 0; i < ExternalFillQuotaService.MAX_FILLS_PER_WINDOW; i++) {
            quota.tryAcquire(null);
        }
        assertFalse(quota.tryAcquire("  "), "缺失来源不得成为无限额度旁路");
    }

    @Test
    void storeFailureFailsOpen() {
        ExternalFillQuotaService open = new ExternalFillQuotaService(new AuthAbuseLimiter.Store() {
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
            assertTrue(open.tryAcquire("1.2.3.4"), "计数存储不可用时应放行，不应把搜索链路打死");
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
