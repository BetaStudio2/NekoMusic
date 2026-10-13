package com.neko.music.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 进程内推送总线：按用户精确唤醒、多连接全覆盖、信号合并、注销清理、早到信号不丢失。
 */
class NotificationPushBusTest {

    @Test
    void signalWakesSameUser() throws Exception {
        NotificationPushBus bus = new NotificationPushBus();
        try (NotificationPushBus.StreamSignal signal = bus.subscribe(7)) {
            assertEquals(1, bus.subscriberCount(7));
            bus.signal(7);
            assertTrue(signal.await(1000), "同用户的信号应立刻唤醒");
        }
    }

    @Test
    void signalDoesNotCrossUsers() throws Exception {
        NotificationPushBus bus = new NotificationPushBus();
        try (NotificationPushBus.StreamSignal a = bus.subscribe(1);
             NotificationPushBus.StreamSignal b = bus.subscribe(2)) {
            bus.signal(1);
            assertTrue(a.await(1000));
            assertFalse(b.await(50), "别的用户的信号不应唤醒本连接");
        }
    }

    @Test
    void repeatedSignalsCoalesce() throws Exception {
        NotificationPushBus bus = new NotificationPushBus();
        try (NotificationPushBus.StreamSignal signal = bus.subscribe(9)) {
            bus.signal(9);
            bus.signal(9);
            bus.signal(9);
            assertTrue(signal.await(500));
            assertFalse(signal.await(50), "突发多次写入应合并成一个待处理标记");
        }
    }

    @Test
    void allSubscribersOfUserAreWoken() throws Exception {
        NotificationPushBus bus = new NotificationPushBus();
        try (NotificationPushBus.StreamSignal a = bus.subscribe(3);
             NotificationPushBus.StreamSignal b = bus.subscribe(3)) {
            assertEquals(2, bus.subscriberCount(3));
            CountDownLatch latch = new CountDownLatch(2);
            Thread t1 = new Thread(() -> {
                try {
                    if (a.await(1000)) {
                        latch.countDown();
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
            Thread t2 = new Thread(() -> {
                try {
                    if (b.await(1000)) {
                        latch.countDown();
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
            t1.start();
            t2.start();
            Thread.sleep(100);
            bus.signal(3);
            assertTrue(latch.await(1, TimeUnit.SECONDS), "同用户的两条连接都应被唤醒");
        }
    }

    @Test
    void signalBeforeAwaitIsNotLost() throws Exception {
        NotificationPushBus bus = new NotificationPushBus();
        try (NotificationPushBus.StreamSignal signal = bus.subscribe(13)) {
            bus.signal(13); // 先写后等：容量 1 会留住标记
            assertTrue(signal.await(500), "早于 await 的信号不应丢失");
        }
    }

    @Test
    void awaitTimesOutWhenIdle() throws Exception {
        NotificationPushBus bus = new NotificationPushBus();
        try (NotificationPushBus.StreamSignal signal = bus.subscribe(11)) {
            assertFalse(signal.await(50), "没有写入时应超时返回 false");
        }
    }

    @Test
    void closeUnregistersAndSignalIsNoopAfterwards() {
        NotificationPushBus bus = new NotificationPushBus();
        NotificationPushBus.StreamSignal signal = bus.subscribe(5);
        assertEquals(1, bus.subscriberCount(5));
        signal.close();
        assertEquals(0, bus.subscriberCount(5));
        bus.signal(5);
    }
}
