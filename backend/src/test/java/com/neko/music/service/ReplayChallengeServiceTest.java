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

    private static final String UA = "NekoMusic-android/202601008";
    private static final String CLIENT = "android+202601008";

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
        ReplayChallengeService.Challenge challenge = ReplayChallengeService.issue(8, 4, UA, CLIENT);
        assertNotNull(challenge);

        ReplayChallengeService.Outcome outcome =
                ReplayChallengeService.verify(challenge.id(), solve(challenge), UA, CLIENT);
        assertEquals(ReplayChallengeService.Status.OK, outcome.status());
        // 以题目绑定的数量为准，客户端无法「小批量领题、大批量兑换」
        assertEquals(8, outcome.read());
        assertEquals(4, outcome.write());
        // 题目已被兑换，重放同一题只会得到「不存在」
        assertEquals(ReplayChallengeService.Status.MISSING,
                ReplayChallengeService.verify(challenge.id(), solve(challenge), UA, CLIENT).status());
    }

    @Test
    void challengeIsBoundToStableClientFingerprintAndCannotBeTransferred() {
        // 出口 IP 不在挑战绑定范围内；同一 UA / 客户端标识在换网络后仍能兑换。
        ReplayChallengeService.Challenge challenge = ReplayChallengeService.issue(2, 2, UA, CLIENT);
        assertEquals(ReplayChallengeService.Status.OK,
                ReplayChallengeService.verify(challenge.id(), solve(challenge), UA, CLIENT).status());

        // 换客户端标识 / User-Agent：摘除题目并判定为来源不匹配。
        ReplayChallengeService.Challenge second = ReplayChallengeService.issue(2, 2, UA, CLIENT);
        assertEquals(ReplayChallengeService.Status.MISMATCH,
                ReplayChallengeService.verify(second.id(), solve(second), UA, "pc+202601008").status());

        ReplayChallengeService.Challenge third = ReplayChallengeService.issue(2, 2, UA, CLIENT);
        assertEquals(ReplayChallengeService.Status.MISMATCH,
                ReplayChallengeService.verify(third.id(), solve(third), "NekoMusic-pc/202601008", CLIENT).status());
    }

    @Test
    void challengeCannotBeRedeemedTwice() {
        ReplayChallengeService.Challenge challenge = ReplayChallengeService.issue(1, 1, UA, CLIENT);
        String proof = solve(challenge);

        assertEquals(ReplayChallengeService.Status.OK,
                ReplayChallengeService.verify(challenge.id(), proof, UA, CLIENT).status());
        // 同一题重放（哪怕解答正确）→ 已不存在
        assertEquals(ReplayChallengeService.Status.MISSING,
                ReplayChallengeService.verify(challenge.id(), proof, UA, CLIENT).status());
        assertEquals(0, ReplayChallengeService.pendingCount());
    }

    @Test
    void wrongProofIsRejected() {
        ReplayChallengeService.Challenge challenge = ReplayChallengeService.issue(1, 1, UA, CLIENT);
        assertEquals(ReplayChallengeService.Status.BAD_PROOF,
                ReplayChallengeService.verify(challenge.id(), failingProof(challenge), UA, CLIENT).status());
        // 解答不合格同样消耗题目：同一道题不再留给客户端反复试错，必须重新领题
        assertEquals(ReplayChallengeService.Status.MISSING,
                ReplayChallengeService.verify(challenge.id(), "1", UA, CLIENT).status());
        assertEquals(0, ReplayChallengeService.pendingCount());
    }

    @Test
    void malformedProofIsRejected() {
        ReplayChallengeService.Challenge challenge = ReplayChallengeService.issue(1, 1, UA, CLIENT);
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
        ReplayChallengeService.Challenge challenge = ReplayChallengeService.issue(1, 1, UA, CLIENT);
        String proof = solve(challenge);

        ReplayChallengeService.useClock(
                () -> base + (ReplayChallengeService.CHALLENGE_TTL_SECONDS + 1) * 1000L);
        assertEquals(ReplayChallengeService.Status.EXPIRED,
                ReplayChallengeService.verify(challenge.id(), proof, UA, CLIENT).status());
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
    void elevationRaisesDifficultyWithoutAnyCapAndWidensTtlUpToItsLimit() {
        ReplayChallengeService.Challenge base = ReplayChallengeService.issue(16, 16, UA, CLIENT);
        assertEquals(ReplayChallengeService.BASE_DIFFICULTY_BITS, base.difficulty());
        assertEquals(ReplayChallengeService.CHALLENGE_TTL_SECONDS, base.ttlSeconds());

        // 加压档位写进题目记录：兑换时以记录为准，客户端无法自己「要」一道低难度的题
        ReplayChallengeService.Challenge small = ReplayChallengeService.issue(1, 0, UA, CLIENT, 2);
        assertEquals(ReplayChallengeService.BASE_DIFFICULTY_BITS + 2, small.difficulty());

        // 难度不封顶：叠多少就是多少（越过可发题界线的表达由调用方转成落库标记 + 拒绝）
        ReplayChallengeService.Challenge huge = ReplayChallengeService.issue(16, 16, UA, CLIENT, 20);
        assertEquals(ReplayChallengeService.BASE_DIFFICULTY_BITS + 20, huge.difficulty());
        assertTrue(huge.difficulty() > ReplayChallengeService.MAX_ISSUABLE_DIFFICULTY_BITS);

        // 有效期随难度放宽，但不越过上限：难度无上限，题目有效期有
        assertEquals(ReplayChallengeService.CHALLENGE_TTL_SECONDS
                        + 2 * ReplayChallengeService.TTL_BONUS_SECONDS_PER_BIT,
                small.ttlSeconds());
        assertEquals(ReplayChallengeService.MAX_CHALLENGE_TTL_SECONDS, huge.ttlSeconds());
        assertTrue(huge.ttlSeconds() > base.ttlSeconds());
    }

    @Test
    void batchLadderAndPressureBitsAreIndependentAxes() {
        // 大批量本身就要比加压线更高的难度：加压只能往上叠，绝不能把它压回来
        ReplayChallengeService.Challenge large = ReplayChallengeService.issue(2048, 2048, UA, CLIENT);
        assertEquals(ReplayChallengeService.MAX_DIFFICULTY_BITS, large.difficulty());

        ReplayChallengeService.Challenge largeElevated = ReplayChallengeService.issue(2048, 2048, UA, CLIENT, 6);
        assertEquals(ReplayChallengeService.MAX_DIFFICULTY_BITS + 6, largeElevated.difficulty());
    }

    @Test
    void targetDifficultyMatchesTheIssuedChallenge() {
        // 调用方先用它判越界、再发题，两处必须同源
        ReplayChallengeService.Challenge issued = ReplayChallengeService.issue(16, 16, UA, CLIENT, 5);
        assertEquals(issued.difficulty(), ReplayChallengeService.targetDifficulty(16, 16, 5));
        assertEquals(ReplayChallengeService.BASE_DIFFICULTY_BITS,
                ReplayChallengeService.targetDifficulty(16, 16, -3));
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

    @Test
    void proofCounterAcceptsTheFullLongRangeButNoMore() {
        // 计数器裁剪到 19 位（Long 范围）：高难度档位的解答不会被格式判为非法
        assertEquals(19, ReplayChallengeService.MAX_PROOF_DIGITS);
        assertTrue(ReplayChallengeService.validProof("seed", 0, "1234567890123456789"));
        assertFalse(ReplayChallengeService.validProof("seed", 0, "12345678901234567890"));
        assertFalse(ReplayChallengeService.validProof("seed", 0, "123456789012345678a"));
    }

    private static String solve(ReplayChallengeService.Challenge challenge) {
        return ReplayChallengeSolver.solve(challenge);
    }

    private static String failingProof(ReplayChallengeService.Challenge challenge) {
        return ReplayChallengeSolver.failing(challenge);
    }
}
