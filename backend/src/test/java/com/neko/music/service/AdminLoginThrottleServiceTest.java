package com.neko.music.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理端登录失败限流测试：两个维度（用户名 / 来源 IP）都要能锁定，且锁定只取决于请求次数、
 * 与账号是否存在无关——否则 429 会反过来变成账号枚举信号。
 *
 * <p>另外锁住两个容易被写坏的细节：并发突发不能突破阈值（检查与自增必须是同一次原子操作），
 * 以及大小写变体必须共享同一个桶（认证本身是大小写不敏感的）。</p>
 */
class AdminLoginThrottleServiceTest {

    private static final String USER = "Admin";
    private static final String OTHER_USER = "no_such_user_zzz";
    private static final String IP = "203.0.113.7";
    private static final String OTHER_IP = "198.51.100.9";

    private TestAdminLoginThrottleStore store;
    private AdminLoginThrottleService throttle;

    @BeforeEach
    void setUp() {
        store = new TestAdminLoginThrottleStore();
        throttle = new AdminLoginThrottleService(store);
    }

    /** 连续尝试 {@code times} 次，返回其中被放行（未超阈值）的次数。 */
    private long allowed(String username, String ip, int times) {
        long allowed = 0;
        for (int i = 0; i < times; i++) {
            if (throttle.beginAttempt(username, ip).isEmpty()) {
                allowed++;
            }
        }
        return allowed;
    }

    @Test
    void attemptsBelowThresholdAreAllowed() {
        assertEquals(AdminLoginThrottleService.MAX_FAILURES_PER_USERNAME - 1L,
                allowed(USER, IP, AdminLoginThrottleService.MAX_FAILURES_PER_USERNAME - 1));
    }

    @Test
    void exceedingUsernameThresholdRejectsThatUsernameOnly() {
        assertEquals(AdminLoginThrottleService.MAX_FAILURES_PER_USERNAME, allowed(
                USER, IP, AdminLoginThrottleService.MAX_FAILURES_PER_USERNAME + 1),
                "第 11 次起应被拒");

        long remaining = throttle.beginAttempt(USER, OTHER_IP).orElse(0L);
        assertTrue(remaining > 0, "达到阈值的用户名应被锁定，换 IP 无效");
        assertTrue(remaining <= AdminLoginThrottleService.LOCK_SECONDS);

        assertTrue(throttle.beginAttempt(OTHER_USER, IP).isEmpty(),
                "用户名维度被锁不应牵连其它用户名");
    }

    @Test
    void caseVariantsShareTheSameBucket() {
        // 账号查找是大小写不敏感的（MySQL 默认 *_ci 排序规则），因此 admin/Admin/ADMIN 是同一个账号；
        // 计数键若区分大小写，攻击者就能用 2^len 个变体各开一个空桶，把阈值成倍放大
        assertEquals(AdminLoginThrottleService.MAX_FAILURES_PER_USERNAME, allowed("admin", IP, 11));

        for (String variant : new String[]{"Admin", "ADMIN", "aDmIn", "admiN"}) {
            assertTrue(throttle.beginAttempt(variant, OTHER_IP).isPresent(),
                    "大小写变体必须共享同一锁定桶: " + variant);
        }
        assertTrue(throttle.beginAttempt("admin2", OTHER_IP).isEmpty(), "不同账号不应被牵连");
    }

    @Test
    void unicodeCompatVariantsShareTheSameBucket() {
        // 账号查找用的是数据库排序规则（UCA 类 *_ci），除了大小写还会做 Unicode 兼容折叠：
        // 全角、修饰符字母、数学字母等都会被判为同一个账号。计数键必须同样做 NFKC 归一化，
        // 否则同一账号的每个兼容变体都是一个全新的空桶，把配额放大 N 倍（CWE-178）。
        assertEquals(AdminLoginThrottleService.MAX_FAILURES_PER_USERNAME, allowed("admin", IP, 11));

        String[] variants = {
                "\uff41\uff44\uff4d\uff49\uff4e",                     // ａｄｍｉｎ 全角
                "\u1d43dmin",                                          // ᵃdmin 修饰符 a (U+1D43)
                "\ud835\udc1a\ud835\udc1d\ud835\udc26\ud835\udc22\ud835\udc27", // 𝐚𝐝𝐦𝐢𝐧 数学粗体
                "\uff41dmin",                                          // ａdmin 仅全角 a
                "admi\uff4e",                                          // admiｎ 仅全角 n
        };
        for (String variant : variants) {
            assertTrue(throttle.beginAttempt(variant, OTHER_IP).isPresent(),
                    "Unicode 兼容变体必须共享同一锁定桶: " + variant);
        }

        // 真正不同的账号仍应各自独立，别把归一化做成「见谁折叠谁」
        assertTrue(throttle.beginAttempt("\u7ba1\u7406\u5458", OTHER_IP).isEmpty(),
                "非兼容折叠的不同账号不应被牵连");
    }

    @Test
    void unknownUsernamesAreCountedRejectedAndNotDistinguishable() {
        // 不存在的用户名同样累计并触发限流：计数只看请求次数，不暴露账号存在性
        assertEquals(AdminLoginThrottleService.MAX_FAILURES_PER_USERNAME, allowed(
                OTHER_USER, OTHER_IP, AdminLoginThrottleService.MAX_FAILURES_PER_USERNAME + 1));
        assertTrue(throttle.beginAttempt(OTHER_USER, OTHER_IP).isPresent());
    }

    @Test
    void sourceThresholdLocksThatSourceEvenWhenEveryUsernameDiffers() {
        for (int i = 0; i < AdminLoginThrottleService.MAX_FAILURES_PER_SOURCE; i++) {
            assertTrue(throttle.beginAttempt("random_user_" + i, IP).isEmpty());
        }

        assertTrue(throttle.beginAttempt("anything", IP).isPresent(), "同一来源的失败累计应锁定该来源");
        assertTrue(throttle.beginAttempt("anything", OTHER_IP).isEmpty(), "其它来源不受影响");
    }

    @Test
    void concurrentBurstCannotExceedUsernameThreshold() throws Exception {
        int threads = 64;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(threads);
        AtomicInteger allowed = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            // 每个线程换一个来源 IP，隔离来源维度，只考察「同一个用户名桶」上的并发
            String ip = "198.51.100." + (i + 1);
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    if (throttle.beginAttempt(USER, ip).isEmpty()) {
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
        assertEquals(AdminLoginThrottleService.MAX_FAILURES_PER_USERNAME, allowed.get(),
                "并发突发不能突破阈值：判定与自增必须是同一次原子操作");
    }

    @Test
    void successfulLoginClearsUsernameCounterAndReleasesItsOwnSlot() {
        allowed(USER, IP, 3);
        throttle.recordSuccess(USER, IP);

        // 用户名计数已清空，来源那一格也已退回：再失败满阈值-1 次仍应被放行
        assertEquals(AdminLoginThrottleService.MAX_FAILURES_PER_USERNAME - 1L,
                allowed(USER, IP, AdminLoginThrottleService.MAX_FAILURES_PER_USERNAME - 1));
    }

    @Test
    void cooldownExpiresWithTime() {
        assertEquals(AdminLoginThrottleService.MAX_FAILURES_PER_USERNAME,
                allowed(USER, IP, AdminLoginThrottleService.MAX_FAILURES_PER_USERNAME + 1));
        assertTrue(throttle.beginAttempt(USER, IP).isPresent());

        store.advanceSeconds(AdminLoginThrottleService.FAILURE_WINDOW_SECONDS + 1L);
        assertTrue(throttle.beginAttempt(USER, IP).isEmpty(), "冷却期过后应自动放行");
    }

    @Test
    void storageFailureFailsOpen() {
        store.setDown(true);
        assertEquals(AdminLoginThrottleService.MAX_FAILURES_PER_USERNAME * 2L,
                allowed(USER, IP, AdminLoginThrottleService.MAX_FAILURES_PER_USERNAME * 2),
                "存储不可用时应放行，不能把登录整条链路打死");
    }

    @Test
    void countedKeysAreScopedByDimension() {
        throttle.beginAttempt(USER, IP);
        // 用户名与来源各占一个键，互不覆盖
        assertEquals(2, store.size());
    }
}
