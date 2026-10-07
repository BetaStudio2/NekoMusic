package com.neko.music.service;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 一次性 nonce 签发 / 原子消费的行为测试：重放必须失败，且不存在「拿到就能用」的长期凭证。
 */
class ReplayNonceServiceTest {

    private static TestNonceStore redis;

    @BeforeAll
    static void installStore() {
        redis = new TestNonceStore();
        ReplayNonceService.useStore(redis);
    }

    @AfterAll
    static void restoreStore() {
        ReplayNonceService.useStore(null);
    }

    @BeforeEach
    void reset() {
        redis.setDown(false);
        redis.clear();
    }

    @Test
    void nonceIsSingleUse() {
        List<String> nonces = ReplayNonceService.issue(ReplayNonceService.SCOPE_WRITE, 2, "1.2.3.4");
        assertEquals(2, nonces.size());
        assertEquals(2, redis.size());

        // 首次消费成功
        assertEquals(ReplayNonceService.Result.OK, consume(nonces.get(0), ReplayNonceService.SCOPE_WRITE, "1.2.3.4"));
        // 同一个 nonce 重放：必定失败，这是反重放的核心断言
        assertEquals(ReplayNonceService.Result.REJECTED,
                consume(nonces.get(0), ReplayNonceService.SCOPE_WRITE, "1.2.3.4"));
        // 另一个 nonce 不受影响
        assertEquals(ReplayNonceService.Result.OK, consume(nonces.get(1), ReplayNonceService.SCOPE_WRITE, "1.2.3.4"));
        assertEquals(0, redis.size());
    }

    @Test
    void nonceIsBoundToClientIpAndNotConsumedByWrongIp() {
        String nonce = ReplayNonceService.issue(ReplayNonceService.SCOPE_WRITE, 1, "1.2.3.4").get(0);

        // 换个 IP（nonce 被第三方截获后的典型场景）→ 拒绝
        assertEquals(ReplayNonceService.Result.REJECTED,
                consume(nonce, ReplayNonceService.SCOPE_WRITE, "5.6.7.8"));
        // 值不匹配不删除：真正的持有者仍可用
        assertEquals(ReplayNonceService.Result.OK,
                consume(nonce, ReplayNonceService.SCOPE_WRITE, "1.2.3.4"));
    }

    @Test
    void nonceIsBoundToScope() {
        String readNonce = ReplayNonceService.issue(ReplayNonceService.SCOPE_READ, 1, "1.2.3.4").get(0);
        // 拿读 nonce 去发写请求：拒绝
        assertEquals(ReplayNonceService.Result.REJECTED,
                consume(readNonce, ReplayNonceService.SCOPE_WRITE, "1.2.3.4"));
        // 正确类别仍可用
        assertEquals(ReplayNonceService.Result.OK,
                consume(readNonce, ReplayNonceService.SCOPE_READ, "1.2.3.4"));
    }

    @Test
    void unknownOrExpiredNonceIsRejected() {
        String wellFormedButNeverIssued = "0123456789abcdef0123456789abcdef";
        assertEquals(ReplayNonceService.Result.REJECTED,
                consume(wellFormedButNeverIssued, ReplayNonceService.SCOPE_READ, "1.2.3.4"));
    }

    @Test
    void malformedNonceIsRejectedWithoutTouchingRedis() {
        for (String bad : new String[]{null, "", "   ", "abc", "zzzzzzzzzzzzzzzz",
                "0123456789abcdefg", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0"}) {
            assertFalse(ReplayNonceService.isWellFormed(bad), String.valueOf(bad));
            assertEquals(ReplayNonceService.Result.REJECTED,
                    consume(bad, ReplayNonceService.SCOPE_READ, "1.2.3.4"), String.valueOf(bad));
        }
    }

    @Test
    void redisFailureIsReportedNotSilentlyAccepted() {
        redis.setDown(true);
        List<String> issued = ReplayNonceService.issue(ReplayNonceService.SCOPE_WRITE, 1, "1.2.3.4");
        assertTrue(issued.isEmpty(), "存储故障时不得返回可用 nonce");
        assertEquals(ReplayNonceService.Result.ERROR,
                consume("0123456789abcdef0123456789abcdef", ReplayNonceService.SCOPE_WRITE, "1.2.3.4"));
        redis.setDown(false);
    }

    @Test
    void issueClampsBatchSize() {
        List<String> nonces = ReplayNonceService.issue(ReplayNonceService.SCOPE_READ, 1000, "1.2.3.4");
        assertEquals(ReplayNonceService.MAX_BATCH, nonces.size());
        for (String nonce : nonces) {
            assertTrue(ReplayNonceService.isWellFormed(nonce));
        }
        assertTrue(ReplayNonceService.issue(ReplayNonceService.SCOPE_READ, 0, "1.2.3.4").isEmpty());
    }

    @Test
    void scopeMappingCoversAllProtectedMethods() {
        assertEquals(ReplayNonceService.SCOPE_READ, ReplayNonceService.scopeOf("GET"));
        assertEquals(ReplayNonceService.SCOPE_WRITE, ReplayNonceService.scopeOf("post"));
        assertEquals(ReplayNonceService.SCOPE_WRITE, ReplayNonceService.scopeOf("PUT"));
        assertEquals(ReplayNonceService.SCOPE_WRITE, ReplayNonceService.scopeOf("PATCH"));
        assertEquals(ReplayNonceService.SCOPE_WRITE, ReplayNonceService.scopeOf("DELETE"));
        assertEquals(null, ReplayNonceService.scopeOf("OPTIONS"));
        assertEquals(null, ReplayNonceService.scopeOf(null));
    }

    @Test
    void ipHashHidesRawAddressAndIsStable() {
        String hash = ReplayNonceService.ipHash("203.0.113.9");
        assertNotNull(hash);
        assertFalse(hash.contains("203"));
        assertEquals(hash, ReplayNonceService.ipHash("203.0.113.9"));
        assertFalse(hash.equals(ReplayNonceService.ipHash("203.0.113.10")));
    }

    private static ReplayNonceService.Result consume(String nonce, String scope, String ip) {
        return ReplayNonceService.consume(nonce, scope, ip);
    }

}
