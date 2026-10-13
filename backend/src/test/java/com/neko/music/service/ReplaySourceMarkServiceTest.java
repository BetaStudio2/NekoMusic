package com.neko.music.service;

import com.neko.music.database.ReplaySourceMarkDatabaseManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 越界来源标记测试：标记落库、有效期内的判定与缓存、写库冷却、人工放行、存储故障放行。
 */
class ReplaySourceMarkServiceTest {

    private static final String IP = "203.0.113.9";

    private final TestReplaySourceMarkStore store = new TestReplaySourceMarkStore();
    private final long[] clock = {1_700_000_000_000L};
    private final ReplaySourceMarkService service =
            new ReplaySourceMarkService(store, () -> clock[0]);

    @Test
    void markIsWrittenOnceAndRejectsWhileActive() {
        service.mark(IP, 12_000, 33, ReplaySourceMarkService.REASON_UNSOLVABLE);
        assertEquals(1, store.upserts().size());
        assertTrue(service.isMarked(IP));
        assertTrue(store.rows().containsKey(IP), "标记应落到存储（生产即主库表）");

        // 冷却期内的重复标记不再写库，但判定仍然成立
        service.mark(IP, 12_500, 34, ReplaySourceMarkService.REASON_UNSOLVABLE);
        assertEquals(1, store.upserts().size(), "冷却期内不应重复写库");
        assertTrue(service.isMarked(IP));
    }

    @Test
    void markExpiresWithItsTtl() {
        service.mark(IP, 12_000, 33, ReplaySourceMarkService.REASON_UNSOLVABLE);
        clock[0] += (ReplaySourceMarkService.MARK_TTL_SECONDS + 1) * 1000L;
        store.rows().clear();
        assertFalse(service.isMarked(IP), "标记到期后来源应恢复可领取");
    }

    @Test
    void verdictIsCachedSoTheDatabaseIsNotQueriedPerRequest() {
        assertFalse(service.isMarked(IP));
        assertFalse(service.isMarked(IP));
        assertFalse(service.isMarked(IP));
        assertEquals(1, store.reads(), "短时间内的重复判定应走进程内缓存");

        // 缓存到期后回源一次，仍以存储为准
        clock[0] += (ReplaySourceMarkService.NEGATIVE_CACHE_SECONDS + 1) * 1000L;
        assertFalse(service.isMarked(IP));
        assertEquals(2, store.reads());
    }

    @Test
    void manualClearTakesEffectImmediately() {
        service.mark(IP, 12_000, 33, ReplaySourceMarkService.REASON_UNSOLVABLE);
        assertTrue(service.isMarked(IP));
        assertTrue(service.clear(IP));
        assertFalse(service.isMarked(IP), "人工放行后不应再受缓存影响");
    }

    @Test
    void storeFailureFailsOpen() {
        store.setDown(true);
        service.mark(IP, 12_000, 33, ReplaySourceMarkService.REASON_UNSOLVABLE);
        assertFalse(service.isMarked(IP), "标记层不可用时应放行，而不是把领取链路打死");
    }
}
