package com.neko.music.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 测试用解题器：按服务端公开的契约（对 {@code seed + ":" + counter} 求 SHA-256，前导零比特数达标）
 * 独立实现一遍。多端测试共用，避免各自复制一份循环。
 */
public final class ReplayChallengeSolver {

    private ReplayChallengeSolver() {
    }

    /**
     * 求出一个满足难度的解答。
     *
     * <p>上限取 64M 而不是「够用就行」的小值：难度是 20 比特时平均要试 2^20 ≈ 100 万次，
     * 上限若只留 5 倍余量，约每 150 次运行就会有 1 次因运气不好而失败（0.7% 的随机红）。
     * 64 倍余量把失败概率压到可以忽略，而期望耗时仍然只有毫秒级。</p>
     */
    public static String solve(ReplayChallengeService.Challenge challenge) {
        MessageDigest digest = sha256();
        for (long counter = 0; counter < 64_000_000L; counter++) {
            if (ReplayChallengeService.meetsDifficulty(
                    hash(digest, challenge.seed(), counter), challenge.difficulty())) {
                return Long.toString(counter);
            }
        }
        throw new IllegalStateException("测试解答未在预期次数内完成");
    }

    /** 求一个**不**满足难度的解答，用于验证服务端不会误放行。 */
    public static String failing(ReplayChallengeService.Challenge challenge) {
        MessageDigest digest = sha256();
        for (long counter = 0; counter < 10_000L; counter++) {
            if (!ReplayChallengeService.meetsDifficulty(
                    hash(digest, challenge.seed(), counter), challenge.difficulty())) {
                return Long.toString(counter);
            }
        }
        throw new IllegalStateException("未找到不满足难度的解答");
    }

    private static byte[] hash(MessageDigest digest, String seed, long counter) {
        digest.reset();
        digest.update((seed + ":" + counter).getBytes(StandardCharsets.UTF_8));
        return digest.digest();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
