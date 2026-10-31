package com.neko.music.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * 防重放挑战：领取 nonce 之前，客户端必须先解出服务端现场下发的一道题。
 *
 * <p>为什么需要它：{@code /api/replay/nonce} 是自举接口（不能要求 nonce），若领取完全免费，
 * 攻击者就能按批量上限刷取，把「一次请求最多签发 {@link ReplayNonceService#MAX_BATCH} 个键」
 * 变成廉价的写放大器。挑战机制让**每一个 nonce 都对应一段真实计算**，且批量越大题目越难，
 * 把攻击成本从「改一行请求头」抬到「按算力付钱」；正常客户端只是在领取时多花几十毫秒。</p>
 *
 * <p>成本不对称：服务端验一次哈希，客户端要试 {@code 2^difficulty} 量级次。题目一次性、短时效，
 * 并绑定领取方的来源指纹，无法转卖或换端复用。为避免给 Redis 添负担，挑战只存在进程内
 * （带 TTL 的单次映射），不落库。</p>
 *
 * <p>难度本身**不设上限**：批量档位之上再叠加按来源频率给出的加压档位
 * （{@link ReplayDifficultyPolicy}，档位随频率与持续时间无限累加）。本类只负责"这道题还发不发得
 * 出去"：越过 {@link #MAX_ISSUABLE_DIFFICULTY_BITS} 的表达由调用方转成落库标记 + 拒绝。</p>
 */
public final class ReplayChallengeService {

    private static final Logger logger = LoggerFactory.getLogger(ReplayChallengeService.class);

    /** 挑战有效期：客户端需在此时间内解题并换取 nonce。 */
    public static final int CHALLENGE_TTL_SECONDS = 60;

    /** 解题算法标识：对 {@code seed + ":" + counter} 求 SHA-256，要求前导零比特数 ≥ difficulty。 */
    public static final String ALGORITHM = "sha256-leading-zero-bits";

    /**
     * 基准批量（读 + 写 = {@link #BASE_TOTAL}）对应的难度；每翻一倍批量加 1 比特（计算量翻倍）。
     *
     * <p>取值权衡：这是**抬高自动化成本**而不是设一道墙——难度每加 1 比特只让求解耗时翻倍，
     * 脚本终究能解出来；真正的门槛是签发限额与各入口的速率限制。因此取「脚本明显变贵、正常
     * 浏览器仍在一秒内可解」的档位（客户端为单块 SHA-256 无分配实现，可读响应里的 difficulty 自适应）。</p>
     */
    static final int BASE_DIFFICULTY_BITS = 20;

    /** 基准批量总数。 */
    static final int BASE_TOTAL = 32;

    /** 难度上限：避免批量拉满时把正常客户端卡死。 */
    static final int MAX_DIFFICULTY_BITS = 26;

    /**
     * 还愿意**发题**的难度上限——它不是加压上限，加压阶梯本身没有上限
     * （见 {@link ReplayDifficultyPolicy}）。
     *
     * <p>{@code 2^32} 次哈希（约 43 亿次）已经超出任何客户端在合理解题窗口内的能力：按浏览器
     * 2 MH/s 也要半小时以上。越过这条线还继续发题，唯一的效果是连同出口的正常用户一起锁死
     * ——他们同样解不开。因此调用方改为**落库标记该来源并明确拒绝**（{@link ReplaySourceMarkService}），
     * 把"无限加价"升级成可见、可复核的处置。</p>
     *
     * <p>协议侧更远的硬界：SHA-256 摘要空间 256 位；解答计数器用十进制裁剪到
     * {@link #MAX_PROOF_DIGITS} 位（Long 范围）。两者都远在本线之外，本线纯粹是"人还解不解得开"。</p>
     */
    public static final int MAX_ISSUABLE_DIFFICULTY_BITS = 32;

    /** 每加压 1 比特，题目有效期放宽的秒数。 */
    static final int TTL_BONUS_SECONDS_PER_BIT = 15;

    /**
     * 题目有效期上限（秒）。
     *
     * <p>难度可以无上限，**有效期不行**：题目在有效期内绑定来源即可兑换，窗口越长越像一次长期凭证。
     * 因此难度继续涨、有效期到顶为止（慢设备也不至于解到一半就过期）。</p>
     */
    static final int MAX_CHALLENGE_TTL_SECONDS = 300;

    /** 解答计数器的最大位数（Long 范围），对应协议侧的硬界。 */
    static final int MAX_PROOF_DIGITS = 19;

    /** 进程内最多暂存的挑战数；超过则先清理过期项，仍然超限就拒绝签发。 */
    private static final int MAX_PENDING = 200_000;

    /** 每签发这么多题，顺手清理一次过期项。 */
    private static final int SWEEP_INTERVAL = 512;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Map<String, Challenge> PENDING = new ConcurrentHashMap<>();

    private static final AtomicInteger ISSUED = new AtomicInteger();

    private static volatile LongSupplier clock = System::currentTimeMillis;

    private ReplayChallengeService() {
    }

    /** 校验结果。 */
    public enum Status {
        /** 通过。 */
        OK,
        /** 挑战不存在或已被使用（含重放）。 */
        MISSING,
        /** 挑战已过期。 */
        EXPIRED,
        /** 挑战与当前客户端指纹不匹配（换 User-Agent / 客户端标识）。 */
        MISMATCH,
        /** 解答不合格。 */
        BAD_PROOF
    }

    /**
     * 校验结论；通过时带回该题绑定的批量，以及这道题的难度（写进访问日志，供安全分析）。
     *
     * <p>失败时难度仅在「题目确实存在且已取出」的情形下才有值——题不存在时无从得知难度，
     * 记 0 表示未知。</p>
     */
    public record Outcome(Status status, int read, int write, int difficulty) {

        static Outcome failed(Status status) {
            return new Outcome(status, 0, 0, 0);
        }

        static Outcome failed(Status status, int difficulty) {
            return new Outcome(status, 0, 0, Math.max(0, difficulty));
        }
    }

    /** 一道待解的挑战。 */
    public record Challenge(String id, String seed, int difficulty, int read, int write,
                            String sourceHash, long expiresAtMillis, int ttlSeconds) {
    }

    /**
     * 下发一道挑战；暂存超限时返回 {@code null}（调用方按服务繁忙处理）。
     */
    public static Challenge issue(int read, int write, String userAgent, String nekoClient) {
        return issue(read, write, userAgent, nekoClient, 0);
    }

    /**
     * 下发一道挑战，并按来源压力叠加加压档位（见 {@link ReplayDifficultyPolicy}）。
     *
     * @param extraBits 服务端按该来源的领取压力给出的加压比特数；{@code 0} 表示基准档
     */
    public static Challenge issue(int read, int write, String userAgent, String nekoClient, int extraBits) {
        long now = clock.getAsLong();
        if (PENDING.size() > MAX_PENDING) {
            sweep(now);
            if (PENDING.size() > MAX_PENDING) {
                logger.warn("防重放挑战积压过多，拒绝签发: pending={}", PENDING.size());
                return null;
            }
        } else if (ISSUED.incrementAndGet() % SWEEP_INTERVAL == 0) {
            sweep(now);
        }
        int elevation = Math.max(0, extraBits);
        int difficulty = targetDifficulty(read, write, elevation);
        int ttlSeconds = challengeTtlSeconds(elevation);
        Challenge challenge = new Challenge(
                randomHex(16),
                randomHex(16),
                difficulty,
                read,
                write,
                ReplayNonceService.sourceHash(userAgent, nekoClient),
                now + ttlSeconds * 1000L,
                ttlSeconds);
        PENDING.put(challenge.id(), challenge);
        return challenge;
    }

    /**
     * 校验解答。挑战先摘除再校验，因此**同一道题只能兑换一次**，失败也要重新领题；
     * 这样既杜绝重放，也不给「在同一道题上反复试探」留空间。
     */
    public static Outcome verify(String challengeId, String proof, String userAgent, String nekoClient) {
        if (challengeId == null || challengeId.isBlank()) {
            return Outcome.failed(Status.MISSING);
        }
        Challenge challenge = PENDING.remove(challengeId.trim());
        if (challenge == null) {
            return Outcome.failed(Status.MISSING);
        }
        if (challenge.expiresAtMillis() < clock.getAsLong()) {
            return Outcome.failed(Status.EXPIRED, challenge.difficulty());
        }
        if (!challenge.sourceHash().equals(ReplayNonceService.sourceHash(userAgent, nekoClient))) {
            return Outcome.failed(Status.MISMATCH, challenge.difficulty());
        }
        if (!validProof(challenge.seed(), challenge.difficulty(), proof)) {
            return Outcome.failed(Status.BAD_PROOF, challenge.difficulty());
        }
        return new Outcome(Status.OK, challenge.read(), challenge.write(), challenge.difficulty());
    }

    /**
     * 叠加加压档位后的目标难度：{@code 批量档位 + 加压档位}，**不封顶**。
     *
     * <p>单独暴露给调用方，是为了让"是否已越过可解线"的判定与实际发题用同一个算式，避免两处漂移；
     * 越过 {@link #MAX_ISSUABLE_DIFFICULTY_BITS} 时调用方不发题，改走标记处置。</p>
     */
    public static int targetDifficulty(int read, int write, int extraBits) {
        return difficultyFor(read + write) + Math.max(0, extraBits);
    }

    /** 题目有效期：随加压档位放宽，但不越过 {@link #MAX_CHALLENGE_TTL_SECONDS}。 */
    static int challengeTtlSeconds(int extraBits) {
        int bonus = Math.max(0, extraBits) * TTL_BONUS_SECONDS_PER_BIT;
        return Math.min(CHALLENGE_TTL_SECONDS + bonus, MAX_CHALLENGE_TTL_SECONDS);
    }

    /** 批量越大题目越难：每翻一倍批量加 1 比特，上限 {@link #MAX_DIFFICULTY_BITS}。 */
    static int difficultyFor(int total) {
        int bits = BASE_DIFFICULTY_BITS;
        int value = Math.max(1, total);
        while (value > BASE_TOTAL) {
            bits++;
            value >>= 1;
        }
        return Math.min(bits, MAX_DIFFICULTY_BITS);
    }

    /** 解答必须是十进制计数，且其摘要满足难度要求。 */
    static boolean validProof(String seed, int difficulty, String proof) {
        if (proof == null) {
            return false;
        }
        String value = proof.trim();
        if (value.isEmpty() || value.length() > MAX_PROOF_DIGITS) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return meetsDifficulty(digest(seed, value), difficulty);
    }

    /** 摘要前导零比特数是否达到要求。 */
    static boolean meetsDifficulty(byte[] digest, int bits) {
        int fullBytes = bits / 8;
        int remainingBits = bits % 8;
        if (digest.length < fullBytes + (remainingBits > 0 ? 1 : 0)) {
            return false;
        }
        for (int i = 0; i < fullBytes; i++) {
            if (digest[i] != 0) {
                return false;
            }
        }
        if (remainingBits == 0) {
            return true;
        }
        int mask = (0xFF << (8 - remainingBits)) & 0xFF;
        return (digest[fullBytes] & mask) == 0;
    }

    private static byte[] digest(String seed, String counter) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((seed + ":" + counter).getBytes(StandardCharsets.UTF_8));
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 不支持 SHA-256", e);
        }
    }

    private static void sweep(long now) {
        PENDING.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() < now);
    }

    private static String randomHex(int bytes) {
        byte[] buf = new byte[bytes];
        RANDOM.nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }

    /** 测试用：当前暂存的挑战数。 */
    static int pendingCount() {
        return PENDING.size();
    }

    /** 测试用：清空暂存并重置计数。 */
    public static void reset() {
        PENDING.clear();
        ISSUED.set(0);
        clock = System::currentTimeMillis;
    }

    /** 测试用：替换时钟，用于复现过期场景。 */
    static void useClock(LongSupplier supplier) {
        clock = supplier == null ? System::currentTimeMillis : supplier;
    }
}
