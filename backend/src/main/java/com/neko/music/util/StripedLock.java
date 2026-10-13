package com.neko.music.util;

import java.util.concurrent.locks.ReentrantLock;

/**
 * 固定条带锁：把任意 key 稳定映射到固定数量的 {@link ReentrantLock} 之一，保证「同一 key 串行」，
 * 且内存占用与 key 数量无关（对比按 key 建 {@code ConcurrentHashMap<String, Lock>} 会随 key 无限增长）。
 *
 * <p>并发度上限（如补全线程池 ≤10）远小于条带数时，不同 key 落到同一条带的碰撞概率很低；
 * 偶发碰撞只是让两个无关 key 短暂串行，不影响正确性。</p>
 */
public final class StripedLock {

    private final ReentrantLock[] locks;

    public StripedLock(int stripes) {
        if (stripes <= 0) {
            throw new IllegalArgumentException("条带数必须为正数");
        }
        this.locks = new ReentrantLock[stripes];
        for (int i = 0; i < stripes; i++) {
            locks[i] = new ReentrantLock();
        }
    }

    /** 取 key 对应的锁：同一 key（{@code equals}/{@code hashCode} 一致）恒定返回同一把锁。 */
    public ReentrantLock forKey(Object key) {
        int hash = key == null ? 0 : key.hashCode();
        return locks[(hash & 0x7fffffff) % locks.length];
    }

    /** 条带总数：与 key 数量无关，用于确认内存占用有界。 */
    public int stripeCount() {
        return locks.length;
    }
}
