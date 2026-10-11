package com.neko.music.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户端登录失败限流测试：邮箱维度单账号封顶（换 IP 不能绕过）、来源维度封顶、成功回退，
 * 以及与管理端共用引擎时两边的计数桶必须互相隔离。
 */
class UserLoginThrottleServiceTest {

    private static final String EMAIL = "User@Example.com";
    private static final String OTHER_EMAIL = "nobody_zzz@example.com";
    private static final String IP = "203.0.113.7";
    private static final String OTHER_IP = "198.51.100.9";

    private TestAdminLoginThrottleStore store;
    private UserLoginThrottleService throttle;

    @BeforeEach
    void setUp() {
        store = new TestAdminLoginThrottleStore();
        throttle = new UserLoginThrottleService(store);
    }

    private long allowed(String email, String ip, int times) {
        long allowed = 0;
        for (int i = 0; i < times; i++) {
            if (throttle.beginAttempt(email, ip).isEmpty()) {
                allowed++;
            }
        }
        return allowed;
    }

    @Test
    void attemptsBelowThresholdAreAllowed() {
        assertEquals(UserLoginThrottleService.MAX_FAILURES_PER_EMAIL - 1L,
                allowed(EMAIL, IP, UserLoginThrottleService.MAX_FAILURES_PER_EMAIL - 1));
    }

    @Test
    void exceedingEmailThresholdLocksThatEmailOnly() {
        assertEquals(UserLoginThrottleService.MAX_FAILURES_PER_EMAIL,
                allowed(EMAIL, IP, UserLoginThrottleService.MAX_FAILURES_PER_EMAIL + 1),
                "第 " + (UserLoginThrottleService.MAX_FAILURES_PER_EMAIL + 1) + " 次起应被拒");

        assertTrue(throttle.beginAttempt(EMAIL, OTHER_IP).isPresent(),
                "邮箱维度达到阈值后，换来源 IP 仍应锁定");
        assertTrue(throttle.beginAttempt(OTHER_EMAIL, IP).isEmpty(),
                "邮箱维度被锁不应牵连其它邮箱");
    }

    @Test
    void caseVariantsShareTheSameBucket() {
        assertEquals(UserLoginThrottleService.MAX_FAILURES_PER_EMAIL,
                allowed("user@example.com", IP, UserLoginThrottleService.MAX_FAILURES_PER_EMAIL + 1));

        for (String variant : new String[]{"User@Example.com", "USER@EXAMPLE.COM", "uSeR@eXaMpLe.CoM"}) {
            assertTrue(throttle.beginAttempt(variant, OTHER_IP).isPresent(),
                    "邮箱大小写变体必须共享同一锁定桶: " + variant);
        }
    }

    @Test
    void unicodeCompatVariantsShareTheSameBucket() {
        // 邮箱比较同样受数据库排序规则影响，全角等兼容变体会折叠成同一账号；计数键必须一致归一化
        assertEquals(UserLoginThrottleService.MAX_FAILURES_PER_EMAIL,
                allowed("user@example.com", IP, UserLoginThrottleService.MAX_FAILURES_PER_EMAIL + 1));

        for (String variant : new String[]{"\uff55ser@example.com", "user@\uff45xample.com"}) {
            assertTrue(throttle.beginAttempt(variant, OTHER_IP).isPresent(),
                    "邮箱兼容变体必须共享同一锁定桶: " + variant);
        }
        assertTrue(throttle.beginAttempt("other@example.com", OTHER_IP).isEmpty(),
                "不同邮箱不应被牵连");
    }

    @Test
    void unknownEmailsAreCountedAndLockedToo() {
        assertEquals(UserLoginThrottleService.MAX_FAILURES_PER_EMAIL,
                allowed(OTHER_EMAIL, OTHER_IP, UserLoginThrottleService.MAX_FAILURES_PER_EMAIL + 1));
        assertTrue(throttle.beginAttempt(OTHER_EMAIL, OTHER_IP).isPresent());
    }

    @Test
    void sourceThresholdLocksThatSourceEvenWhenEveryEmailDiffers() {
        for (int i = 0; i < UserLoginThrottleService.MAX_FAILURES_PER_SOURCE; i++) {
            assertTrue(throttle.beginAttempt("random_" + i + "@example.com", IP).isEmpty());
        }
        assertTrue(throttle.beginAttempt("anything@example.com", IP).isPresent(),
                "同一来源的失败累计应锁定该来源");
        assertTrue(throttle.beginAttempt("anything@example.com", OTHER_IP).isEmpty(),
                "其它来源不受影响");
    }

    @Test
    void concurrentBurstCannotExceedEmailThreshold() throws Exception {
        int threads = 64;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(threads);
        AtomicInteger allowed = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            String ip = "198.51.100." + (i + 1);
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    if (throttle.beginAttempt(EMAIL, ip).isEmpty()) {
                        allowed.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            });
            thread.start();
        }

        start.countDown();
        assertTrue(finished.await(10, TimeUnit.SECONDS), "并发用例超时");
        assertEquals(UserLoginThrottleService.MAX_FAILURES_PER_EMAIL, allowed.get(),
                "并发突发不能突破阈值：判定与自增必须是同一次原子操作");
    }

    @Test
    void successfulLoginClearsEmailCounterAndReleasesItsOwnSlot() {
        allowed(EMAIL, IP, 3);
        throttle.recordSuccess(EMAIL, IP);

        assertEquals(UserLoginThrottleService.MAX_FAILURES_PER_EMAIL - 1L,
                allowed(EMAIL, IP, UserLoginThrottleService.MAX_FAILURES_PER_EMAIL - 1));
    }

    @Test
    void cooldownExpiresWithTime() {
        assertEquals(UserLoginThrottleService.MAX_FAILURES_PER_EMAIL,
                allowed(EMAIL, IP, UserLoginThrottleService.MAX_FAILURES_PER_EMAIL + 1));
        assertTrue(throttle.beginAttempt(EMAIL, IP).isPresent());

        store.advanceSeconds(UserLoginThrottleService.FAILURE_WINDOW_SECONDS + 1L);
        assertTrue(throttle.beginAttempt(EMAIL, IP).isEmpty(), "冷却期过后应自动放行");
    }

    @Test
    void storageFailureFailsOpen() {
        store.setDown(true);
        assertEquals(UserLoginThrottleService.MAX_FAILURES_PER_EMAIL * 2L,
                allowed(EMAIL, IP, UserLoginThrottleService.MAX_FAILURES_PER_EMAIL * 2),
                "存储不可用时应放行，不能把登录整条链路打死");
    }

    @Test
    void adminAndUserBucketsDoNotCollide() {
        // 两边共用同一套引擎与存储，但键前缀不同：一个账号被管理端锁死，不应同时锁住同名的用户端账号
        AdminLoginThrottleService admin = new AdminLoginThrottleService(store);
        for (int i = 0; i <= AdminLoginThrottleService.MAX_FAILURES_PER_USERNAME; i++) {
            admin.beginAttempt("shared", IP);
        }
        assertTrue(admin.beginAttempt("shared", IP).isPresent(), "管理端应已锁定");
        assertTrue(throttle.beginAttempt("shared", IP).isEmpty(),
                "用户端计数的键前缀必须与管理端隔离");
    }

    @Test
    void countedKeysAreScopedByDimension() {
        // 邮箱与来源各占一个键，互不覆盖
        throttle.beginAttempt(EMAIL, IP);
        assertEquals(2, store.size());
    }
}
