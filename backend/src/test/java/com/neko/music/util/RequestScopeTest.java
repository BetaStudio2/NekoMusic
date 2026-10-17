package com.neko.music.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 单请求作用域：只在 {@code begin()} 与 {@code end()} 之间缓存，结束后必须读不到上一个请求的值
 * （Jetty 线程复用，残留会把上一个请求的路径 / 令牌结果串给下一个请求）。
 */
class RequestScopeTest {

    @AfterEach
    void tearDown() {
        RequestScope.end();
    }

    @Test
    void cachesWithinScopeAndClearsOnEnd() {
        assertFalse(RequestScope.active());
        RequestScope.put("k", "v");
        assertNull(RequestScope.get("k"), "没有作用域时写入是 no-op");

        RequestScope.begin();
        assertTrue(RequestScope.active());
        RequestScope.put("k", "v");
        assertEquals("v", RequestScope.get("k"));

        RequestScope.end();
        assertFalse(RequestScope.active());
        assertNull(RequestScope.get("k"), "作用域结束后不能读到上一个请求的值");
    }

    @Test
    void computeIfAbsentRunsLoaderOncePerRequest() {
        RequestScope.begin();
        AtomicInteger loads = new AtomicInteger();
        String first = RequestScope.computeIfAbsent("k", () -> "v" + loads.incrementAndGet());
        String second = RequestScope.computeIfAbsent("k", () -> "v" + loads.incrementAndGet());
        assertEquals("v1", first);
        assertEquals("v1", second);
        assertEquals(1, loads.get());
    }

    @Test
    void negativeResultIsCachedToo() {
        RequestScope.begin();
        AtomicInteger loads = new AtomicInteger();
        assertNull(RequestScope.computeIfAbsent("missing", () -> {
            loads.incrementAndGet();
            return null;
        }));
        assertNull(RequestScope.computeIfAbsent("missing", () -> {
            loads.incrementAndGet();
            return null;
        }));
        assertEquals(1, loads.get(), "无效令牌这类否定结果同样只查一次");
    }

    @Test
    void withoutScopeLoaderAlwaysRuns() {
        AtomicInteger loads = new AtomicInteger();
        RequestScope.computeIfAbsent("k", () -> {
            loads.incrementAndGet();
            return "v";
        });
        RequestScope.computeIfAbsent("k", () -> {
            loads.incrementAndGet();
            return "v";
        });
        assertEquals(2, loads.get(), "没有作用域时退化为直接计算，不做跨请求缓存");
    }

    @Test
    void beginStartsFreshScope() {
        RequestScope.begin();
        RequestScope.put("k", "v1");
        RequestScope.begin();
        assertNull(RequestScope.get("k"), "新请求不能看到上一个请求的值");
    }
}
