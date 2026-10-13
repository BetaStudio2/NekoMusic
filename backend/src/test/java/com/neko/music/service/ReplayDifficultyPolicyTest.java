package com.neko.music.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 动态难度策略测试：正常速率不加压、速率档随频率逐位上升、持续档无上限累加、
 * 安静期后回落、存储故障退回基准档、来源之间互不牵连。
 */
class ReplayDifficultyPolicyTest {

    private static final String IP = "203.0.113.9";
    private static final String OTHER_IP = "198.51.100.7";

    private final TestReplayPressureStore store = new TestReplayPressureStore();

    @BeforeEach
    void setUp() {
        ReplayDifficultyPolicy.useStore(store);
    }

    @AfterEach
    void tearDown() {
        ReplayDifficultyPolicy.reset();
    }

    @Test
    void normalRateIsNotElevated() {
        // 正常客户端一次领满 16+16，一轮只贡献 2 次计数，十分钟内远到不了基准线
        store.seed(IP, ReplayDifficultyPolicy.BASE_REQUESTS, 0);
        assertEquals(0, ReplayDifficultyPolicy.extraBits(IP));
        assertTrue(store.levelWrites() == 0, "未踩持续线不应写档位键");
    }

    @Test
    void eachDoublingOfTheWindowRateAddsOneBitWithoutAnyCap() {
        for (int doublings = 1; doublings <= 12; doublings++) {
            store.seed(IP, ReplayDifficultyPolicy.BASE_REQUESTS * (1L << doublings), 0);
            assertEquals(doublings, ReplayDifficultyPolicy.extraBits(IP),
                    "窗口计数每翻一倍应加 1 比特（第 " + doublings + " 档）");
        }
    }

    @Test
    void sustainedRateAccumulatesLevelsWithoutLadderCap() {
        // 每踩一次线记一档，档位随持续时长无限累加，没有"最高档"这回事
        store.seed(IP, ReplayDifficultyPolicy.SUSTAINED_REQUESTS + 1, 0);
        int previous = 0;
        for (int windows = 1; windows <= 15; windows++) {
            store.advanceSeconds(ReplayDifficultyPolicy.PRESSURE_WINDOW_SECONDS);
            int bits = ReplayDifficultyPolicy.extraBits(IP);
            assertTrue(bits > previous, "持续踩线的来源只会越来越贵（第 " + windows + " 个窗口）");
            previous = bits;
        }
        assertEquals(ReplayDifficultyPolicy.rateBits(store.requests())
                        + 15 * ReplayDifficultyPolicy.STEP_BITS,
                previous);
        assertTrue(previous > ReplayChallengeService.MAX_ISSUABLE_DIFFICULTY_BITS,
                "持续踩线最终应越过可发题的界线，改由落库标记处置");
    }

    @Test
    void strikeLevelDecaysAfterAQuietPeriod() {
        store.seed(IP, ReplayDifficultyPolicy.SUSTAINED_REQUESTS + 1, 0);
        int elevated = ReplayDifficultyPolicy.extraBits(IP);
        assertTrue(elevated > 0);

        // 安静期内不再请求：计数窗口过期、档位安静期到点，两边都回到基准
        store.seed(IP, 0, 0);
        store.advanceSeconds(ReplayDifficultyPolicy.LEVEL_QUIET_SECONDS + 1);
        assertEquals(0, ReplayDifficultyPolicy.extraBits(IP));
    }

    @Test
    void rateAndStrikeBitsAddUpInOneObservation() {
        ReplayDifficultyPolicy.Pressure observed = new ReplayDifficultyPolicy.Pressure(
                ReplayDifficultyPolicy.BASE_REQUESTS * (1L << 7), 4);
        assertEquals(7 + 4 * ReplayDifficultyPolicy.STEP_BITS, ReplayDifficultyPolicy.extraBits(observed));
    }

    @Test
    void measureReturnsTheSameObservationUsedForBits() {
        store.seed(IP, ReplayDifficultyPolicy.BASE_REQUESTS * 4, 3);
        ReplayDifficultyPolicy.Pressure observed = ReplayDifficultyPolicy.measure(IP);
        assertEquals(store.requests(), observed.requests(), "越界留痕要用与定价同一次观测的计数");
        assertEquals(3, observed.level());
        assertEquals(2 + 3 * ReplayDifficultyPolicy.STEP_BITS, ReplayDifficultyPolicy.extraBits(observed));
    }

    @Test
    void pressureIsPerSourceAndDoesNotLeakToOthers() {
        store.seed(IP, ReplayDifficultyPolicy.SUSTAINED_REQUESTS + 1, 0);
        assertTrue(ReplayDifficultyPolicy.extraBits(IP) > 0);
        assertEquals(0, ReplayDifficultyPolicy.extraBits(OTHER_IP));
    }

    @Test
    void storeFailureFallsBackToBaseDifficulty() {
        store.setDown(true);
        assertEquals(-1, ReplayDifficultyPolicy.observe(IP));
        assertEquals(0, ReplayDifficultyPolicy.extraBits(IP));
    }

    @Test
    void overflowGuardSitsFarBeyondAnyIssuableDifficulty() {
        // 保险丝只防溢出：极端输入下也不会绕回小数，且仍远高于可发题界线
        int bits = ReplayDifficultyPolicy.extraBits(new ReplayDifficultyPolicy.Pressure(Long.MAX_VALUE, 10_000));
        assertEquals(ReplayDifficultyPolicy.MAX_RATE_BITS + ReplayDifficultyPolicy.MAX_STRIKE_LEVEL
                * ReplayDifficultyPolicy.STEP_BITS, bits);
        assertTrue(bits > ReplayChallengeService.MAX_ISSUABLE_DIFFICULTY_BITS);
    }

    @Test
    void rateBitsBoundaryIsExclusiveAtTheBaseline() {
        assertEquals(0, ReplayDifficultyPolicy.rateBits(ReplayDifficultyPolicy.BASE_REQUESTS));
        // 整数除法：还没翻倍就仍是基准档
        assertEquals(0, ReplayDifficultyPolicy.rateBits(ReplayDifficultyPolicy.BASE_REQUESTS * 2 - 1));
        assertEquals(1, ReplayDifficultyPolicy.rateBits(ReplayDifficultyPolicy.BASE_REQUESTS * 2));
        assertEquals(0, ReplayDifficultyPolicy.rateBits(-1));
        // 计数不可用时绝不能因为读不到计数而"加压"
        assertEquals(0, ReplayDifficultyPolicy.extraBits(ReplayDifficultyPolicy.Pressure.UNAVAILABLE));
    }
}
