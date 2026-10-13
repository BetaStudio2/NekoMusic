package com.neko.music.service;

import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 单机进程内的站内消息推送总线：把「写库」与「等待中的 SSE 连接」直接接起来。
 *
 * <p>不做集群时，消息写入与 SSE 长连接都在同一个 JVM 里，本地写完就能立刻唤醒对应用户的连接，
 * 因此不必让每条连接每秒去轮询一次 Redis。单连接成本从「每秒一次 Redis GET」降到「真正发生写入
 * 才唤醒一次」，空闲连接的开销趋近于 0——这才是单机把在线连接数做大（而不是靠一个全局上限挡住
 * 后来者）的前提。</p>
 *
 * <p>唤醒是「合并式」的：同一用户的多条连接各有一个容量为 1 的信号槽，写入期间的多次通知只会留下
 * 一个待处理标记，连接醒来后按游标一次性补齐所有新消息即可，不会因突发写入堆积信号。</p>
 *
 * <p>它是纯内存结构、无外部依赖；Redis 版本号仍然保留为兜底（见
 * {@link UserNotificationService#currentVersion(int)}），用于覆盖「信号丢失 / 未来出现旁路写入」
 * 这类情况。</p>
 */
public final class NotificationPushBus {

    private static final Object WAKEUP = new Object();

    private final ConcurrentHashMap<Integer, Set<StreamSignal>> signals = new ConcurrentHashMap<>();

    /** 为一个用户注册一条等待中的连接；用完必须 {@link StreamSignal#close()} 注销。 */
    public StreamSignal subscribe(int userId) {
        StreamSignal signal = new StreamSignal(userId);
        signals.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(signal);
        return signal;
    }

    /** 该用户有新消息时调用：唤醒其全部等待中的连接。没有连接在等时是空操作。 */
    public void signal(int userId) {
        Set<StreamSignal> set = signals.get(userId);
        if (set == null || set.isEmpty()) {
            return;
        }
        for (StreamSignal signal : set) {
            signal.wake();
        }
    }

    /** 当前在册的连接数，仅用于观测与测试。 */
    public int subscriberCount(int userId) {
        Set<StreamSignal> set = signals.get(userId);
        return set == null ? 0 : set.size();
    }

    /** 一条 SSE 连接的唤醒句柄。 */
    public final class StreamSignal implements AutoCloseable {
        private final int userId;
        /** 容量 1：并发写入被合并成一个待处理标记，避免信号堆积。 */
        private final BlockingQueue<Object> wakeups = new LinkedBlockingQueue<>(1);

        private StreamSignal(int userId) {
            this.userId = userId;
        }

        private void wake() {
            wakeups.offer(WAKEUP);
        }

        /**
         * 等待被唤醒。
         *
         * @return {@code true} 收到信号（可能有新消息）；{@code false} 超时（调用方回落到一次兜底比对）
         */
        public boolean await(long timeoutMillis) throws InterruptedException {
            return wakeups.poll(timeoutMillis, TimeUnit.MILLISECONDS) != null;
        }

        @Override
        public void close() {
            signals.computeIfPresent(userId, (key, set) -> {
                set.remove(this);
                return set.isEmpty() ? null : set;
            });
        }
    }
}
