package com.neko.music.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 成品响应缓存：TTL 内复用同一份字符串（不重复查询 / 序列化），过期或显式失效后重新生成，
 * 生成失败不落缓存。
 */
class JsonResponseCacheTest {

    @AfterEach
    void tearDown() {
        JsonResponseCache.clear();
    }

    @Test
    void reusesBodyWithinTtl() {
        AtomicInteger loads = new AtomicInteger();
        String first = JsonResponseCache.get("k", 60_000, () -> "body" + loads.incrementAndGet());
        String second = JsonResponseCache.get("k", 60_000, () -> "body" + loads.incrementAndGet());
        assertEquals("body1", first);
        assertEquals("body1", second);
        assertEquals(1, loads.get());
    }

    @Test
    void expiredEntryIsRegenerated() {
        AtomicInteger loads = new AtomicInteger();
        JsonResponseCache.get("k", 0, () -> "body" + loads.incrementAndGet());
        String second = JsonResponseCache.get("k", 0, () -> "body" + loads.incrementAndGet());
        assertEquals("body2", second);
        assertEquals(2, loads.get());
    }

    @Test
    void invalidateForcesRegeneration() {
        AtomicInteger loads = new AtomicInteger();
        JsonResponseCache.get("k", 60_000, () -> "body" + loads.incrementAndGet());
        JsonResponseCache.invalidate("k");
        String second = JsonResponseCache.get("k", 60_000, () -> "body" + loads.incrementAndGet());
        assertEquals("body2", second);
    }

    @Test
    void failedLoadIsNotCached() {
        AtomicInteger loads = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> JsonResponseCache.get("k", 60_000, () -> {
            loads.incrementAndGet();
            throw new IllegalStateException("数据库不可用");
        }));
        String recovered = JsonResponseCache.get("k", 60_000, () -> "body" + loads.incrementAndGet());
        assertEquals("body2", recovered);
    }
}
