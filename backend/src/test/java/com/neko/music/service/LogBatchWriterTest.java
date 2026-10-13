package com.neko.music.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 异步批量写入器：它处在每个请求的返回路径上，行为必须是「入队不阻塞、关闭不丢存量」。
 */
class LogBatchWriterTest {

    @Test
    @DisplayName("关闭时把队列里的存量全部刷盘")
    void flushesOnClose() {
        CopyOnWriteArrayList<String> written = new CopyOnWriteArrayList<>();
        LogBatchWriter<String> writer = new LogBatchWriter<>("test", written::addAll, 64, 8, 20);
        writer.start();
        for (int i = 0; i < 20; i++) {
            writer.offer("entry-" + i);
        }
        writer.close();

        assertEquals(20, written.size(), "关闭时必须把存量写出去");
        assertEquals(20, writer.writtenCount());
        assertEquals(0, writer.droppedCount());
    }

    @Test
    @DisplayName("队列满时丢弃并计数，绝不阻塞调用线程")
    void dropsWhenQueueIsFull() {
        AtomicInteger batches = new AtomicInteger();
        // 不 start()：写入线程未运行，队列不会被消费，可稳定观察丢弃行为
        LogBatchWriter<String> writer = new LogBatchWriter<>("test", batch -> batches.incrementAndGet(), 4, 4, 20);
        for (int i = 0; i < 10; i++) {
            writer.offer("entry-" + i);
        }
        assertEquals(4, writer.queueSize());
        assertEquals(6, writer.droppedCount());
        assertTrue(batches.get() == 0);
        writer.close();
        assertEquals(0, batches.get(), "未启动写入线程时不应写入");
    }

    @Test
    @DisplayName("未启用时不接收任何条目")
    void stopsAcceptingAfterClose() {
        List<String> written = new CopyOnWriteArrayList<>();
        LogBatchWriter<String> writer = new LogBatchWriter<>("test", written::addAll, 8, 8, 20);
        writer.start();
        writer.close();
        writer.offer("late");
        assertEquals(0, written.size());
    }
}
