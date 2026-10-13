package com.neko.music.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 测试用 nonce 存储：内存实现 {@link ReplayNonceService.Store}，语义与 Redis 实现一致
 * （NX 占位、命中即删、值不匹配不删、可模拟整体故障）。
 *
 * <p>刻意不继承 {@code RedisService}：单元测试不创建 Lettuce 客户端、不连真实 Redis，
 * 也不会在打包日志里打出“已连接到Redis”。</p>
 */
public class TestNonceStore implements ReplayNonceService.Store {

    private final Map<String, String> store = new ConcurrentHashMap<>();
    private final AtomicBoolean down = new AtomicBoolean(false);
    private final AtomicInteger batchCalls = new AtomicInteger();

    /** 模拟存储整体不可用：写返回 false、消费返回 null。 */
    public void setDown(boolean value) {
        down.set(value);
    }

    public int size() {
        return store.size();
    }

    public void clear() {
        store.clear();
        batchCalls.set(0);
    }

    /** 批量签发调用次数：用于断言「整批只往返存储一次」。 */
    public int batchCalls() {
        return batchCalls.get();
    }

    @Override
    public boolean putIfAbsent(String key, String value, int ttlSeconds) {
        if (down.get()) {
            return false;
        }
        return store.putIfAbsent(key, value) == null;
    }

    @Override
    public List<Boolean> putManyIfAbsent(List<String> keys, String value, int ttlSeconds) {
        batchCalls.incrementAndGet();
        if (down.get()) {
            return null;
        }
        List<Boolean> granted = new ArrayList<>(keys.size());
        for (String key : keys) {
            granted.add(store.putIfAbsent(key, value) == null);
        }
        return granted;
    }

    @Override
    public ReplayNonceService.ConsumeResult consume(String key, String expectedIpHash) {
        if (down.get()) {
            return ReplayNonceService.ConsumeResult.STORAGE_ERROR;
        }
        String current = store.get(key);
        // 存储值为 <来源哈希>|<难度>，比对只看竖线前的来源哈希（与生产 Lua 一致）
        String owner = current == null ? null : current.split("\\|", 2)[0];
        if (owner == null || !owner.equals(expectedIpHash)) {
            return ReplayNonceService.ConsumeResult.MISS;
        }
        int difficulty = 0;
        int sep = current.indexOf('|');
        if (sep >= 0) {
            try {
                difficulty = Integer.parseInt(current.substring(sep + 1));
            } catch (NumberFormatException ignored) {
                difficulty = 0;
            }
        }
        store.remove(key);
        return ReplayNonceService.ConsumeResult.granted(difficulty);
    }
}
