package com.neko.music.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 固定条带锁：同一 key 恒定映射到同一把锁、内存占用与 key 数量无关、并发的同键更新被串行化。
 */
class StripedLockTest {

    @Test
    void sameKeyAlwaysMapsToSameLock() {
        StripedLock locks = new StripedLock(64);
        assertSame(locks.forKey("晴天-周杰伦"), locks.forKey("晴天-周杰伦"), "同一 key 必须命中同一把锁");
        assertSame(locks.forKey(123456789L), locks.forKey(123456789L));
        assertSame(locks.forKey(null), locks.forKey(null), "null key 也要稳定映射");
        assertNotSame(locks.forKey("a"), locks.forKey("b"), "不同 key 允许（通常）落到不同条带");
    }

    @Test
    void stripeCountIsFixedRegardlessOfKeyCount() {
        StripedLock locks = new StripedLock(256);
        assertEquals(256, locks.stripeCount());
        // 对比早期按 key 建 ConcurrentHashMap 的实现：映射再多 key 也不会新增锁实例。
        for (int i = 0; i < 100_000; i++) {
            assertNotNull(locks.forKey("key-" + i));
        }
        assertEquals(256, locks.stripeCount(), "锁实例总数与 key 数量无关，内存占用有界");
    }

    @Test
    void singleStripeSerializesConcurrentUpdates() throws InterruptedException {
        StripedLock locks = new StripedLock(1);
        AtomicInteger counter = new AtomicInteger();
        int threads = 8;
        int perThread = 5_000;
        CountDownLatch start = new CountDownLatch(1);
        Thread[] workers = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            workers[t] = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int i = 0; i < perThread; i++) {
                    ReentrantLock lock = locks.forKey("same-key");
                    lock.lock();
                    try {
                        counter.set(counter.get() + 1);
                    } finally {
                        lock.unlock();
                    }
                }
            });
        }
        for (Thread worker : workers) {
            worker.start();
        }
        start.countDown();
        for (Thread worker : workers) {
            worker.join();
        }
        assertEquals(threads * perThread, counter.get(), "同键并发更新必须被串行化，不能丢更新");
    }

    @Test
    void rejectsNonPositiveStripeCount() {
        assertThrows(IllegalArgumentException.class, () -> new StripedLock(0));
        assertThrows(IllegalArgumentException.class, () -> new StripedLock(-1));
    }
}
