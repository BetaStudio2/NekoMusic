package com.neko.music.service;

import com.neko.music.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * 通用请求防重放：一次性 nonce 的签发与原子消费。
 *
 * <p>工作方式：客户端先向 {@code GET /api/replay/nonce} 批量领取 nonce（随机 128 位），
 * 每个动态请求带一个 {@code X-Neko-Nonce}；服务端在 Redis 里以 Lua {@code GET + 比对 + DEL}
 * 原子消费，**同一个 nonce 第二次使用必定失败**，这正是「重放」被拦住的地方。nonce 同时绑定
 * 「客户端 IP 哈希 + 读/写类别」，因此被第三方截获后既不能换 IP 使用，也不能拿读请求的 nonce
 * 去发写请求。</p>
 *
 * <p>为什么不用 HMAC 签名：本仓库开源，任何写进代码或配置模板的密钥都等同公开；一旦密钥泄露，
 * 签名方案可以无限伪造，而随机 nonce 存在服务端、一次性、短时效，泄露一个也只能用一次。
 * 这也是「凭证被非法获取后拿到就能用」这一诉求的答案——nonce 本身就不是长期凭证。</p>
 *
 * <p>nonce 只绑定「IP + 读/写类别」而不绑定具体路径，是为了允许客户端批量预取（一次请求换一批
 * nonce），把每个动态请求的握手成本摊薄到 1/N。跨端点替换 nonce 需要攻击者先截获一个尚未被消费
 * 的 nonce，而该 nonce 又绑定受害者 IP，实际不可用。</p>
 */
public final class ReplayNonceService {

    private static final Logger logger = LoggerFactory.getLogger(ReplayNonceService.class);

    /** nonce 有效期：够客户端批量预取并在这段时间内用掉，过期即自然作废。 */
    public static final int NONCE_TTL_SECONDS = 120;

    /** 单次批量签发上限，避免一次请求把 Redis 写爆。 */
    public static final int MAX_BATCH = 64;

    /** 客户端未指定数量时的默认批量。 */
    public static final int DEFAULT_BATCH = 16;

    /** 读类别（GET/HEAD）：用于有服务端成本的只读接口，重放同样被拒。 */
    public static final String SCOPE_READ = "r";

    /** 写类别（POST/PUT/PATCH/DELETE）：状态变更请求。 */
    public static final String SCOPE_WRITE = "w";

    /** 请求头名；PC / Android 客户端接入时保持一致。 */
    public static final String NONCE_HEADER = "X-Neko-Nonce";

    private static final String KEY_PREFIX = "replay_nonce:";

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Store REDIS_STORE = new RedisStore();

    private static volatile Store store = REDIS_STORE;

    private ReplayNonceService() {
    }

    /**
     * nonce 存储抽象：生产实现是 Redis（{@link RedisStore}），测试可注入内存实现，
     * 从而让单元测试完全不依赖真实 Redis。
     */
    public interface Store {

        /** {@code SET NX EX}：true = 占用成功；false = 已存在或存储故障。 */
        boolean putIfAbsent(String key, String value, int ttlSeconds);

        /**
         * 批量 {@code SET NX EX}：一次往返写入多个键，返回与 {@code keys} 等长的成功标记；
         * {@code null} = 存储故障。默认实现退化为逐个写入，Redis 实现走单条 Lua。
         */
        default List<Boolean> putManyIfAbsent(List<String> keys, String value, int ttlSeconds) {
            List<Boolean> granted = new ArrayList<>(keys.size());
            for (String key : keys) {
                granted.add(putIfAbsent(key, value, ttlSeconds));
            }
            return granted;
        }

        /** 原子「GET + 比对 + DEL」：1 = 消费成功，0 = 不存在 / 不匹配，{@code null} = 存储故障。 */
        Long consumeIfMatches(String key, String expectedValue);
    }

    /** 仅供单元测试替换存储实现；生产代码请勿调用（传 {@code null} 恢复 Redis 实现）。 */
    public static void useStore(Store replacement) {
        store = replacement == null ? REDIS_STORE : replacement;
    }

    /** Redis 实现：用 Lua 保证「比对 + 删除」原子，避免并发或重放出现两个成功。 */
    private static final class RedisStore implements Store {

        /** 原子消费：值必须等于调用方给出的 IP 哈希，成功后立即删除。返回 1 成功 / 0 失败。 */
        private static final String CONSUME_LUA =
                "local v = redis.call('GET', KEYS[1]) "
                        + "if not v then return 0 end "
                        + "if v ~= ARGV[1] then return 0 end "
                        + "redis.call('DEL', KEYS[1]) "
                        + "return 1";

        /** 批量签发：一次往返完成 N 个 {@code SET NX EX}，避免「每个 nonce 一次借还连接」。 */
        private static final String ISSUE_LUA =
                "local granted = {} "
                        + "for i = 1, #KEYS do "
                        + "  local ok = redis.call('SET', KEYS[i], ARGV[1], 'NX', 'EX', ARGV[2]) "
                        + "  if ok then granted[i] = 1 else granted[i] = 0 end "
                        + "end "
                        + "return granted";

        @Override
        public boolean putIfAbsent(String key, String value, int ttlSeconds) {
            return Main.getRedisService().setIfAbsentWithExpiry(key, value, ttlSeconds);
        }

        @Override
        public List<Boolean> putManyIfAbsent(List<String> keys, String value, int ttlSeconds) {
            if (keys == null || keys.isEmpty()) {
                return List.of();
            }
            List<Object> raw = Main.getRedisService().evalMulti(
                    ISSUE_LUA, keys.toArray(new String[0]), new String[]{value, Integer.toString(ttlSeconds)});
            if (raw == null || raw.size() != keys.size()) {
                return null;
            }
            List<Boolean> granted = new ArrayList<>(keys.size());
            for (Object item : raw) {
                granted.add(item instanceof Number number && number.longValue() == 1L);
            }
            return granted;
        }

        @Override
        public Long consumeIfMatches(String key, String expectedValue) {
            Object result = Main.getRedisService().eval(
                    CONSUME_LUA, new String[]{key}, new String[]{expectedValue});
            return result instanceof Number number ? number.longValue() : null;
        }
    }

    /** 消费结果：成功 / 被拒（不存在、不匹配或已重放）/ Redis 故障。 */
    public enum Result {
        OK, REJECTED, ERROR
    }

    /**
     * 批量签发 nonce。一次 Lua 往返完成整批 {@code SET NX EX}，把每请求的 Redis 往返数从 N 降到 1；
     * 存储不可用时返回空列表（由调用方明确报 503，不静默降级）。
     */
    public static List<String> issue(String scope, int count, String clientIp) {
        int wanted = Math.max(0, Math.min(count, MAX_BATCH));
        List<String> nonces = new ArrayList<>(wanted);
        if (wanted == 0) {
            return nonces;
        }
        String ipHash = ipHash(clientIp);
        // 128 位随机撞库概率可忽略；重试只用于兜住存储瞬时抖动。
        for (int round = 0; round < 3 && nonces.size() < wanted; round++) {
            int missing = wanted - nonces.size();
            List<String> candidates = new ArrayList<>(missing);
            List<String> keys = new ArrayList<>(missing);
            for (int i = 0; i < missing; i++) {
                String candidate = randomHex(16);
                candidates.add(candidate);
                keys.add(key(scope, candidate));
            }
            List<Boolean> granted = store.putManyIfAbsent(keys, ipHash, NONCE_TTL_SECONDS);
            if (granted == null) {
                logger.error("签发防重放 nonce 失败（存储不可用） scope={}", scope);
                break;
            }
            for (int i = 0; i < candidates.size(); i++) {
                if (Boolean.TRUE.equals(granted.get(i))) {
                    nonces.add(candidates.get(i));
                }
            }
        }
        return nonces;
    }

    /**
     * 原子消费 nonce：同一个 nonce 第二次调用必定失败，这是反重放的关键一步。
     */
    public static Result consume(String nonce, String scope, String clientIp) {
        if (!isWellFormed(nonce)) {
            return Result.REJECTED;
        }
        String normalizedScope = normalizeScope(scope);
        if (normalizedScope == null) {
            return Result.REJECTED;
        }
        Long consumed = store.consumeIfMatches(
                key(normalizedScope, nonce.trim()), ipHash(clientIp));
        if (consumed == null) {
            logger.error("消费防重放 nonce 失败（存储不可用）");
            return Result.ERROR;
        }
        return consumed == 1L ? Result.OK : Result.REJECTED;
    }

    /** 只接受 16–64 位十六进制，避免把任意字符串直接拼进 Redis key。 */
    public static boolean isWellFormed(String nonce) {
        if (nonce == null) {
            return false;
        }
        String value = nonce.trim();
        if (value.length() < 16 || value.length() > 64) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    /** HTTP 方法对应的 nonce 类别；不受保护的方法返回 {@code null}。 */
    public static String scopeOf(String method) {
        if (method == null) {
            return null;
        }
        return switch (method.toUpperCase(Locale.ROOT)) {
            case "GET" -> SCOPE_READ;
            case "POST", "PUT", "PATCH", "DELETE" -> SCOPE_WRITE;
            default -> null;
        };
    }

    /** 只以短哈希形式参与绑定 / 落库，避免在 Redis 里明文堆积客户端地址。 */
    public static String ipHash(String clientIp) {
        return fingerprint(clientIp);
    }

    /** 来源指纹：SHA-256 前 16 位十六进制，用于把来源信息不可逆地绑进 nonce 与挑战。 */
    public static String fingerprint(String value) {
        String text = value == null ? "" : value.trim();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(text.hashCode());
        }
    }

    /**
     * 解析并夹取客户端要领取的批量：缺省用 {@code defaultValue}，非法值兜底默认，
     * 越界夹到 {@code [0, MAX_BATCH]}。挑战与 nonce 两个接口共用，避免两处判定漂移。
     */
    public static int clampBatch(String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return Math.max(0, Math.min(defaultValue, MAX_BATCH));
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return Math.max(0, Math.min(value, MAX_BATCH));
        } catch (NumberFormatException e) {
            return Math.max(0, Math.min(defaultValue, MAX_BATCH));
        }
    }

    private static String normalizeScope(String scope) {
        if (SCOPE_READ.equals(scope) || SCOPE_WRITE.equals(scope)) {
            return scope;
        }
        return null;
    }

    private static String key(String scope, String nonce) {
        return KEY_PREFIX + scope + ":" + nonce;
    }

    private static String randomHex(int bytes) {
        byte[] buf = new byte[bytes];
        RANDOM.nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }
}
