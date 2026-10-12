package com.neko.music.service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 测试用来源封禁存储：内存实现 {@link SourceBanService.Store}，语义与 Redis 实现（单条 Lua）一致
 * ——窗口内失败自增、达阈值升档并落封禁、退避档位有安静期、可模拟整体故障，并带可控时钟。
 *
 * <p>刻意不继承 {@code RedisService}：单元测试不创建 Lettuce 客户端、不连真实 Redis。</p>
 */
public class TestSourceBanStore implements SourceBanService.Store {

    private static final long EPOCH_SECONDS = 1_000_000L;

    private final Map<String, Entry> entries = new HashMap<>();
    private final AtomicLong now = new AtomicLong(EPOCH_SECONDS);
    private boolean down;

    /** 模拟存储整体不可用：读返回 0、记录失败无效。 */
    public void setDown(boolean value) {
        this.down = value;
    }

    /** 推进时钟，用于验证窗口 / 安静期 / 封禁到期。 */
    public void advanceSeconds(long seconds) {
        now.addAndGet(seconds);
    }

    public int size() {
        return entries.size();
    }

    @Override
    public synchronized long recordFailure(String strikesKey, String levelKey, String bannedKey,
                                           int windowSeconds, int threshold, int levelQuietSeconds,
                                           int[] ladderSeconds) {
        if (down) {
            return 0;
        }
        Entry strikes = entryFor(strikesKey, windowSeconds);
        strikes.count++;
        if (strikes.count < threshold) {
            return 0;
        }
        entries.remove(strikesKey);

        Entry level = entryFor(levelKey, levelQuietSeconds);
        level.count++;
        int index = (int) Math.min(level.count, ladderSeconds.length) - 1;
        long duration = ladderSeconds[index];

        Entry banned = new Entry();
        banned.expiresAt = now.get() + duration;
        entries.put(bannedKey, banned);
        return duration;
    }

    @Override
    public synchronized boolean isBanned(String bannedKey) {
        return ttlSeconds(bannedKey) > 0;
    }

    @Override
    public synchronized long ttlSeconds(String bannedKey) {
        if (down) {
            return 0;
        }
        Entry entry = entries.get(bannedKey);
        if (entry == null) {
            return 0;
        }
        long remaining = entry.expiresAt - now.get();
        if (remaining <= 0) {
            entries.remove(bannedKey);
            return 0;
        }
        return remaining;
    }

    @Override
    public synchronized void clearStrikes(String strikesKey) {
        entries.remove(strikesKey);
    }

    /** 取未过期的条目；不存在或已过期则新建并设置 TTL（与 Redis「首次写入设过期」一致）。 */
    private Entry entryFor(String key, int ttlSeconds) {
        Entry entry = entries.get(key);
        if (entry == null || entry.expiresAt <= now.get()) {
            entry = new Entry();
            entry.expiresAt = now.get() + ttlSeconds;
            entries.put(key, entry);
        }
        return entry;
    }

    private static final class Entry {
        private long count;
        private long expiresAt;
    }
}
