package com.neko.music.service;

import java.util.HashMap;
import java.util.Map;

/**
 * 测试用加压计数存储：按 Lua 的语义复现「窗口计数 + 每窗口最多一档 + 安静期」，带可控时钟。
 *
 * <p>{@link #seed} 把来源直接摆到某个状态（固定窗口计数与已有档位），因此用例不必真的发几百次请求。
 * 刻意不继承 {@code RedisService}：单元测试不创建 Lettuce 客户端、不连真实 Redis。</p>
 */
public class TestReplayPressureStore implements ReplayDifficultyPolicy.Store {

    private long nowSeconds = 1_000_000L;
    private boolean down;
    private String seededSource;
    private long seededRequests;
    private long seededLevel;
    private int levelWrites;

    private final Map<String, long[]> counters = new HashMap<>();
    private final Map<String, Long> levels = new HashMap<>();
    private final Map<String, Long> levelExpiry = new HashMap<>();

    /** 模拟存储整体不可用。 */
    public void setDown(boolean value) {
        this.down = value;
    }

    /** 推进时钟，用于验证窗口过窗与安静期回落。 */
    public void advanceSeconds(long seconds) {
        nowSeconds += seconds;
    }

    /**
     * 把某个来源摆到指定状态（窗口计数与已有档位）；未 seed 的来源按真实计数自增，
     * 因此"来源之间互不牵连"这类断言仍然有意义。
     */
    public void seed(String source, long requests, long level) {
        this.seededSource = source;
        this.seededRequests = requests;
        this.seededLevel = level;
        this.levelWrites = 0;
        counters.clear();
        levels.clear();
        levelExpiry.clear();
    }

    /** seed 过的来源当前的窗口计数（用例断言留痕用的是不是同一次观测）。 */
    public long requests() {
        return seededRequests;
    }

    /** 档位键被写了几次（每次踩线应恰好写一次）。 */
    public int levelWrites() {
        return levelWrites;
    }

    @Override
    public ReplayDifficultyPolicy.Pressure incrementAndRead(String pressureKey, String levelKey,
                                                           int windowSeconds, int sustainedRequests,
                                                           int quietSeconds) {
        if (down) {
            return ReplayDifficultyPolicy.Pressure.UNAVAILABLE;
        }
        long count;
        if (seededSource != null && pressureKey.endsWith(seededSource)) {
            count = seededRequests;
        } else {
            long[] window = counters.get(pressureKey);
            if (window == null || window[1] <= nowSeconds) {
                window = new long[]{0L, nowSeconds + windowSeconds};
            }
            count = window[0] + 1;
            counters.put(pressureKey, new long[]{count, window[1]});
        }

        long held = heldLevel(levelKey);
        if (count == sustainedRequests + 1) {
            // 正好越过持续线：本窗口记一档（与 Lua 的等值判定一致）
            held = held + 1;
            levels.put(levelKey, held);
            levelExpiry.put(levelKey, nowSeconds + quietSeconds);
            levelWrites++;
        }
        return new ReplayDifficultyPolicy.Pressure(count, held);
    }

    private long heldLevel(String levelKey) {
        Long expiresAt = levelExpiry.get(levelKey);
        if (expiresAt != null && expiresAt <= nowSeconds) {
            // 安静期到点：档位（含用例注入的初始档）一并归零
            levels.remove(levelKey);
            levelExpiry.remove(levelKey);
            seededLevel = 0;
            return 0;
        }
        Long stored = levels.get(levelKey);
        if (stored != null) {
            return stored;
        }
        boolean seededKey = seededSource != null && levelKey.endsWith(seededSource);
        return seededKey ? seededLevel : 0;
    }
}
