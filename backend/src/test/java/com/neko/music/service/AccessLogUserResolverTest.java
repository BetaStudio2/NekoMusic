package com.neko.music.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 访问日志的「已登录用户昵称」解析：缓存必须挡住重复查库，缺省失败必须降级为「只有 ID」。
 */
class AccessLogUserResolverTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);

    @AfterEach
    void tearDown() {
        AccessLogUserResolver.reset();
    }

    @Test
    @DisplayName("昵称按 ID 解析并缓存：同一 ID 在 TTL 内只查一次库")
    void cachesNicknamePerUser() {
        Map<Integer, String> names = new HashMap<>(Map.of(7, "小明", 9, "小红"));
        Map<Integer, Integer> queries = new HashMap<>();
        AccessLogUserResolver.useClock(now::get);
        AccessLogUserResolver.useLoader(userId -> {
            queries.merge(userId, 1, Integer::sum);
            return names.get(userId);
        });

        assertEquals("小明", AccessLogUserResolver.nicknameOf(7));
        assertEquals("小明", AccessLogUserResolver.nicknameOf(7));
        assertEquals("小红", AccessLogUserResolver.nicknameOf(9));
        assertEquals(1, queries.get(7));
        assertEquals(1, queries.get(9));

        // TTL 到期后重新查一次，库里的新昵称才会被日志看到
        names.put(7, "小明改名了");
        now.addAndGet(AccessLogUserResolver.POSITIVE_TTL_SECONDS * 1000L + 1);
        assertEquals("小明改名了", AccessLogUserResolver.nicknameOf(7));
        assertEquals(2, queries.get(7));
    }

    @Test
    @DisplayName("查不到的用户只缓存一小段负结果，且不返回空串以外的假身份")
    void cachesMissingUserBriefly() {
        AtomicLong calls = new AtomicLong();
        AccessLogUserResolver.useClock(now::get);
        AccessLogUserResolver.useLoader(userId -> {
            calls.incrementAndGet();
            return null;
        });

        assertNull(AccessLogUserResolver.nicknameOf(404));
        assertNull(AccessLogUserResolver.nicknameOf(404));
        assertEquals(1, calls.get());

        now.addAndGet(AccessLogUserResolver.NEGATIVE_TTL_SECONDS * 1000L + 1);
        assertNull(AccessLogUserResolver.nicknameOf(404));
        assertEquals(2, calls.get());
    }

    @Test
    @DisplayName("非法 ID 与全空白昵称都不查库、不落缓存")
    void ignoresInvalidInput() {
        AtomicLong calls = new AtomicLong();
        AccessLogUserResolver.useLoader(userId -> {
            calls.incrementAndGet();
            return "   ";
        });

        assertNull(AccessLogUserResolver.nicknameOf(null));
        assertNull(AccessLogUserResolver.nicknameOf(0));
        assertNull(AccessLogUserResolver.nicknameOf(-3));
        assertEquals(0, calls.get());
        assertNull(AccessLogUserResolver.nicknameOf(5));
        assertEquals(1, calls.get());
    }
}
