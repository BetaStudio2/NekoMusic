package com.neko.music.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 日志异步批量写入器。
 *
 * <p>访问日志由请求线程产出，如果同步落库会把数据库往返时间加到每个请求上，也容易被慢查询反压。
 * 因此这里用「有界队列 + 单个写入线程」把落库与请求解耦：请求线程只做一次 {@code offer}，
 * 写入线程按批（{@code batchSize}）或按空闲（{@code drainMillis}）刷盘。</p>
 *
 * <p>队列满时不阻塞业务：丢弃并计数，由周期性日志提示；这保证日志库故障/积压不会拖垮站点。</p>
 */
public final class LogBatchWriter<T> implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(LogBatchWriter.class);

    /** 单次落库的最大条数（受 MySQL max_allowed_packet 与单事务时长约束，不宜过大）。 */
    private static final int DEFAULT_BATCH_SIZE = 200;
    /** 队列空闲多久后把已有条目刷盘（毫秒）。 */
    private static final int DEFAULT_DRAIN_MILLIS = 1000;
    /** 队列容量：按峰值 QPS × 数秒估算，满了就丢并计数，不阻塞请求。 */
    private static final int DEFAULT_QUEUE_CAPACITY = 65536;
    /** 关闭时等待存量刷盘的最长时间（毫秒）。 */
    private static final long CLOSE_JOIN_MILLIS = 10000L;

    /** 一批日志的落库动作；异常由写入线程捕获，不向上传播。 */
    @FunctionalInterface
    public interface Sink<T> {
        void write(List<T> batch) throws Exception;
    }

    private final String name;
    private final BlockingQueue<T> queue;
    private final int batchSize;
    private final int drainMillis;
    private final Sink<T> sink;
    private final Thread thread;
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong written = new AtomicLong();
    private volatile boolean running = true;

    public LogBatchWriter(String name, Sink<T> sink) {
        this(name, sink, DEFAULT_QUEUE_CAPACITY, DEFAULT_BATCH_SIZE, DEFAULT_DRAIN_MILLIS);
    }

    LogBatchWriter(String name, Sink<T> sink, int queueCapacity, int batchSize, int drainMillis) {
        this.name = name;
        this.sink = sink;
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.batchSize = batchSize;
        this.drainMillis = drainMillis;
        this.thread = new Thread(this::run, name + "-writer");
        this.thread.setDaemon(true);
    }

    public void start() {
        thread.start();
    }

    /** 入队；队列已满时丢弃并计数，绝不阻塞调用线程。 */
    public void offer(T entry) {
        if (!running || entry == null) {
            return;
        }
        if (!queue.offer(entry)) {
            long total = dropped.incrementAndGet();
            // 限流告警：丢第一条与之后每万条各提示一次，避免刷屏也不至于静默丢数据
            if (total == 1 || total % 10000 == 0) {
                logger.warn("{} 队列已满，已丢弃 {} 条（写入速度跟不上请求速率）", name, total);
            }
        }
    }

    private void run() {
        List<T> batch = new ArrayList<>(batchSize);
        while (running || !queue.isEmpty()) {
            batch.clear();
            try {
                T first = queue.poll(drainMillis, TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }
                batch.add(first);
                queue.drainTo(batch, batchSize - 1);
                sink.write(batch);
                written.addAndGet(batch.size());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                failed.addAndGet(batch.size());
                logger.warn("{} 批量写入失败（本批 {} 条已丢弃）: {}", name, batch.size(), e.getMessage());
            }
        }
    }

    public long droppedCount() {
        return dropped.get();
    }

    public long failedCount() {
        return failed.get();
    }

    public long writtenCount() {
        return written.get();
    }

    public int queueSize() {
        return queue.size();
    }

    @Override
    public void close() {
        running = false;
        try {
            thread.join(CLOSE_JOIN_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (!queue.isEmpty() || dropped.get() > 0 || failed.get() > 0) {
            logger.info("{} 关闭：已写入={}, 丢弃={}, 失败={}, 残留={}",
                    name, written.get(), dropped.get(), failed.get(), queue.size());
        }
    }
}
