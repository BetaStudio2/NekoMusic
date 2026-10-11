package com.neko.music.service;

import com.neko.music.model.Admin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理员会话生命周期：登录轮换、登出吊销、改密/删号批量吊销。
 *
 * <p>用内存桩替代 {@link RedisService}（不建 Lettuce 客户端、不连 Redis），只验证
 * {@link AdminAuthService} 是否把「先吊销旧会话、再签发新令牌」等动作真正下发到 token store。</p>
 */
class AdminSessionLifecycleTest {

    /** 记录调用的 token store 桩：维护 token → adminId 映射，方法语义与 Redis 实现一致。 */
    private static final class RecordingTokenStore extends RedisTokenStore {
        final List<String> events = new ArrayList<>();
        final Map<String, Integer> sessions = new HashMap<>();

        RecordingTokenStore() {
            super(null);
        }

        @Override
        public void saveAdminToken(String token, int adminId, int ttlSeconds) {
            sessions.put(token, adminId);
            events.add("save:" + adminId);
        }

        @Override
        public Optional<Integer> getAdminIdByToken(String token) {
            return Optional.ofNullable(sessions.get(RedisTokenStore.normalizeBearerToken(token)));
        }

        @Override
        public boolean deleteAdminToken(String token) {
            events.add("delete");
            return sessions.remove(RedisTokenStore.normalizeBearerToken(token)) != null;
        }

        @Override
        public int revokeAllAdminTokens(int adminId) {
            events.add("revokeAll:" + adminId);
            int removed = 0;
            for (Iterator<Map.Entry<String, Integer>> it = sessions.entrySet().iterator(); it.hasNext(); ) {
                if (it.next().getValue() == adminId) {
                    it.remove();
                    removed++;
                }
            }
            return removed;
        }
    }

    private static Admin admin(int id) {
        return new Admin(id, "admin", "hash", "a@b.c", true, "super_admin", 0L, 0L);
    }

    @Test
    @DisplayName("管理员重新登录先吊销此前全部会话，旧令牌立即失效")
    void reloginRotatesAdminSession() {
        RecordingTokenStore store = new RecordingTokenStore();
        AdminAuthService service = new AdminAuthService(null, store);

        String first = service.createAdminSession(admin(7));
        assertNotNull(first);
        assertEquals(List.of("revokeAll:7", "save:7"), store.events);

        String second = service.createAdminSession(admin(7));
        assertNotEquals(first, second);
        assertEquals(List.of("revokeAll:7", "save:7", "revokeAll:7", "save:7"), store.events);

        assertTrue(service.getAdminIdByToken(first).isEmpty(), "轮换后旧令牌必须已失效");
        assertEquals(Optional.of(7), service.getAdminIdByToken(second));
    }

    @Test
    @DisplayName("登出只吊销当前令牌，改密/删号走批量吊销")
    void logoutAndRevokeAllReachTokenStore() {
        RecordingTokenStore store = new RecordingTokenStore();
        AdminAuthService service = new AdminAuthService(null, store);
        String token = service.createAdminSession(admin(3));

        assertTrue(service.logout(token));
        assertTrue(service.getAdminIdByToken(token).isEmpty(), "登出后令牌必须立即失效，而不是等 24h TTL");

        service.revokeAllAdminTokens(9);
        assertEquals("revokeAll:9", store.events.get(store.events.size() - 1));
    }
}
