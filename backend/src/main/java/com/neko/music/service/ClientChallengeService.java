package com.neko.music.service;

import com.neko.music.util.ClientIdentityProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 客户端实例注册 / 令牌续签的一次性挑战。
 *
 * <p>挑战只存进程内、短时有效，并绑定客户端 ID、平台、来源 IP 与 User-Agent；注册与续签都必须
 * 先消费它。它不是长期凭证，也不承担请求防重放——动态请求仍由现有 nonce 机制保护。</p>
 */
public final class ClientChallengeService {

    private static final Logger logger = LoggerFactory.getLogger(ClientChallengeService.class);
    public static final int CHALLENGE_TTL_SECONDS = 300;
    private static final int MAX_PENDING = 100_000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<String, PendingChallenge> PENDING = new ConcurrentHashMap<>();
    private static volatile LongSupplier clock = System::currentTimeMillis;

    private ClientChallengeService() {
    }

    public enum Purpose {
        REGISTER,
        TOKEN
    }

    public enum Status {
        OK,
        MISSING,
        EXPIRED,
        MISMATCH
    }

    private record PendingChallenge(String clientId, String platform, Purpose purpose,
                                    String ipHash, String uaHash, long expiresAtMillis) {
    }

    /** 签发挑战；待处理挑战过多时返回 {@code null}，调用方应回 429/503。 */
    public static String issue(String clientId, String platform, Purpose purpose,
                               String clientIp, String userAgent) {
        long now = clock.getAsLong();
        if (PENDING.size() >= MAX_PENDING) {
            PENDING.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() < now);
            if (PENDING.size() >= MAX_PENDING) {
                logger.warn("客户端挑战待处理过多，拒绝签发: {}", PENDING.size());
                return null;
            }
        }
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String challenge = ClientIdentityProtocol.base64Url(raw);
        PENDING.put(challenge, new PendingChallenge(
                clientId, platform, purpose,
                ReplayNonceService.fingerprint(clientIp),
                ReplayNonceService.fingerprint(userAgent),
                now + CHALLENGE_TTL_SECONDS * 1000L));
        return challenge;
    }

    /** 原子摘除并校验。无论成功与否，同一挑战都不能再用。 */
    public static Status consume(String challenge, String clientId, String platform, Purpose purpose,
                                 String clientIp, String userAgent) {
        if (challenge == null || challenge.isBlank()) {
            return Status.MISSING;
        }
        PendingChallenge pending = PENDING.remove(challenge.trim());
        if (pending == null) {
            return Status.MISSING;
        }
        if (pending.expiresAtMillis() < clock.getAsLong()) {
            return Status.EXPIRED;
        }
        if (!pending.clientId().equals(clientId)
                || !pending.platform().equals(platform)
                || pending.purpose() != purpose
                || !pending.ipHash().equals(ReplayNonceService.fingerprint(clientIp))
                || !pending.uaHash().equals(ReplayNonceService.fingerprint(userAgent))) {
            return Status.MISMATCH;
        }
        return Status.OK;
    }

    /** 测试用：替换时钟。 */
    static void useClock(LongSupplier supplier) {
        clock = supplier == null ? System::currentTimeMillis : supplier;
    }

    /** 测试用：清空待处理挑战。 */
    static void reset() {
        PENDING.clear();
        clock = System::currentTimeMillis;
    }

    static int pendingCount() {
        return PENDING.size();
    }
}
