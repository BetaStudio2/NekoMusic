package com.neko.music.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 防重放挑战的行为测试：题目绑定来源、只能兑换一次、批量越大越难、过期即失效。
 *
 * <p>测试自己实现一遍解题循环（与服务端约定一致），因此也顺带回归了「算法描述是否可被独立实现」。</p>
 */
class ReplayChallengeServiceTest {

    private static final String IP = "203.0.113.9";
    private static final String UA = "NekoMusic-android/202601008";

    @BeforeEach
    void setUp() {
        ReplayChallengeService.reset();
    }

    @AfterEach
    void tearDown() {
        ReplayChallengeService.reset();
    }

    @Test
    void correctProofIsAcceptedOnceAndBatchComesFromChallenge() {
        ReplayChallengeService.Challenge challenge = ReplayChallengeService.issue(8, 4, IP, UA);
        assertNotNull(challenge);

        ReplayChallengeService.Outcome outcome =
                ReplayChallengeService.verify(challenge.id(), solve(challenge), IP, UA);
        assertEquals(ReplayChallengeService.Status.OK, outcome.status());
        // 以题目绑定的数量为准，客户端无法「小批量领题、大批量兑换」
        assertEquals(8, outcome.read());
        assertEquals(4, outcome.write());
        // 题目已被兑换，重放同一题只会得到「不存在」
        assertEquals(ReplayChallengeService.Status.MISSING,
                ReplayChallengeService.verify(challenge.id(), solve(challenge), IP, UA).status());
    }

    @Test
    void challengeIsBoundToSourceAndCannotBeTransferred() {
        ReplayChallengeService.Challenge challenge = ReplayChallengeService.issue(2, 2, IP, UA);
        String proof = solve(challenge);

        // 换 IP：题目被摘除且判定为来源不匹配
        assertEquals(ReplayChallengeService.Status.MISMATCH,
                ReplayChallengeService.verify(challenge.id(), proof, "198.51.100.7", UA).status());
        // 换 User-Agent 同理
        ReplayChallengeService.Challenge second = ReplayChallengeService.issue(2, 2, IP, UA);
        assertEquals(ReplayChallengeService.Status.MISMATCH,
                ReplayChallengeService.verify(second.id(), solve(second), IP, "NekoMusic-pc/202601008").status());
    }

    @Test
    void challengeCannotBeRedeemedTwice() {
        ReplayChallengeService.Challenge challenge = ReplayChallengeService.issue(1, 1, IP, UA);
        String proof = solve(challenge);

        assertEquals(ReplayChallengeService.Status.OK,
                ReplayChallengeService.verify(challenge.id(), proof, IP, UA).status());
        // 同一题重放（哪怕解答正确）→ 已不存在
        assertEquals(ReplayChallengeService.Status.MISSING,
                ReplayChallengeService.verify(challenge.id(), proof, IP, UA).status());
        assertEquals(0, ReplayChallengeService.pendingCount());
    }

    @Test
    void wrongProofIsRejected() {
        ReplayChallengeService.Challenge challenge = ReplayChallengeService.issue(1, 1, IP, UA);
        assertEquals(ReplayChallengeService.Status.BAD_PROOF,
                ReplayChallengeService.verify(challenge.id(), failingProof(challenge), IP, UA).status());
        // 解答不合格同样消耗题目：同一道题不再留给客户端反复试错，必须重新领题
        assertEquals(ReplayChallengeService.Status.MISSING,
                ReplayChallengeService.verify(challenge.id(), "1", IP, UA).status());
        assertEquals(0, ReplayChallengeService.pendingCount());
    }

    @Test
    void malformedProofIsRejected() {
        ReplayChallengeService.Challenge challenge = ReplayChallengeService.issue(1, 1, IP, UA);
        assertFalse(ReplayChallengeService.validProof(challenge.seed(), challenge.difficulty(), null));
        assertFalse(ReplayChallengeService.validProof(challenge.seed(), challenge.difficulty(), ""));
        assertFalse(ReplayChallengeService.validProof(challenge.seed(), challenge.difficulty(), "-1"));
        assertFalse(ReplayChallengeService.validProof(challenge.seed(), challenge.difficulty(), "0x10"));
        assertFalse(ReplayChallengeService.validProof(challenge.seed(), challenge.difficulty(), "1234567890123"));
    }

    @Test
    void expiredChallengeIsRejected() {
        long base = 1_700_000_000_000L;
        ReplayChallengeService.useClock(() -> base);
        ReplayChallengeService.Challenge challenge = ReplayChallengeService.issue(1, 1, IP, UA);
        String proof = solve(challenge);

        ReplayChallengeService.useClock(
                () -> base + (ReplayChallengeService.CHALLENGE_TTL_SECONDS + 1) * 1000L);
        assertEquals(ReplayChallengeService.Status.EXPIRED,
                ReplayChallengeService.verify(challenge.id(), proof, IP, UA).status());
    }

    @Test
    void difficultyGrowsWithBatchAndIsCapped() {
        assertEquals(ReplayChallengeService.BASE_DIFFICULTY_BITS,
                ReplayChallengeService.difficultyFor(ReplayChallengeService.BASE_TOTAL));
        assertEquals(ReplayChallengeService.BASE_DIFFICULTY_BITS + 1, ReplayChallengeService.difficultyFor(64));
        assertEquals(ReplayChallengeService.BASE_DIFFICULTY_BITS + 2, ReplayChallengeService.difficultyFor(128));
        // 批量拉满时也不会把正常客户端卡死
        assertEquals(ReplayChallengeService.MAX_DIFFICULTY_BITS, ReplayChallengeService.difficultyFor(4096));
    }

    @Test
    void meetsDifficultyChecksLeadingZeroBits() {
        assertTrue(ReplayChallengeService.meetsDifficulty(new byte[]{0x00, 0x00}, 16));
        assertTrue(ReplayChallengeService.meetsDifficulty(new byte[]{0x00, 0x1F}, 11));
        assertFalse(ReplayChallengeService.meetsDifficulty(new byte[]{0x00, 0x20}, 11));
        assertFalse(ReplayChallengeService.meetsDifficulty(new byte[]{0x01, 0x00}, 8));
        assertTrue(ReplayChallengeService.meetsDifficulty(new byte[]{0x01, 0x00}, 7));
        // 摘要长度不足时不得误判为通过
        assertFalse(ReplayChallengeService.meetsDifficulty(new byte[]{0x00}, 16));
    }

    private static String solve(ReplayChallengeService.Challenge challenge) {
        return ReplayChallengeSolver.solve(challenge);
    }

    private static String failingProof(ReplayChallengeService.Challenge challenge) {
        return ReplayChallengeSolver.failing(challenge);
    }
}
