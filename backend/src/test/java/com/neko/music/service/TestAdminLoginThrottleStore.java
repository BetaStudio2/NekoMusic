package com.neko.music.service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 测试用管理端登录限流存储：内存实现 {@link AdminLoginThrottleService.Store}，
 * 语义与 Redis 实现一致（窗口内原子自增、原子退回、可模拟整体故障），并带可控时钟。
 *
 * <p>刻意不继承 {@code RedisService}：单元测试不创建 Lettuce 客户端、不连真实 Redis。</p>
 */
public class TestAdminLoginThrottleStore implements AdminLoginThrottleService.Store {

    private static final long EPOCH_SECONDS = 1_000_000L;

    private final Map<String, Entry> entries = new HashMap<>();
    private final AtomicLong now = new AtomicLong(EPOCH_SECONDS);
    private boolean down;

    /** 模拟存储整体不可用：读返回 0、自增返回 -1、写入无效。 */
    public void setDown(boolean value) {
        this.down = value;
    }

    /** 推进时钟，用于验证锁定到期后自动解锁。 */
    public void advanceSeconds(long seconds) {
        now.addAndGet(seconds);
    }

    /** 当前时钟（秒），用于断言剩余 TTL。 */
    public long nowSeconds() {
        return now.get();
    }

    public int size() {
        return entries.size();
    }

    @Override
    public synchronized long ttlSeconds(String key) {
        if (down) {
            return 0;
        }
        Entry entry = entries.get(key);
        if (entry == null) {
            return 0;
        }
        long remaining = entry.expiresAt - now.get();
        if (remaining <= 0) {
            entries.remove(key);
            return 0;
        }
        return remaining;
    }

    @Override
    public synchronized long incrementInWindow(String key, int windowSeconds) {
        if (down) {
            return -1;
        }
        Entry entry = entries.get(key);
        if (entry == null || entry.expiresAt <= now.get()) {
            entry = new Entry();
            entry.expiresAt = now.get() + windowSeconds;
            entries.put(key, entry);
        }
        entry.count++;
        return entry.count;
    }

    @Override
    public synchronized void release(String key) {
        if (down) {
            return;
        }
        Entry entry = entries.get(key);
        if (entry == null) {
            return;
        }
        if (--entry.count <= 0) {
            entries.remove(key);
        }
    }

    @Override
    public synchronized void delete(String key) {
        entries.remove(key);
    }

    private static final class Entry {
        private long count;
        private long expiresAt;
    }
}
