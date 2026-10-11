package com.neko.music.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理端登录失败限流测试：两个维度（用户名 / 来源 IP）都要能锁定，且锁定只取决于请求次数、
 * 与账号是否存在无关——否则 429 会反过来变成账号枚举信号。
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

    private void fail(String username, String ip, int times) {
        for (int i = 0; i < times; i++) {
            throttle.recordFailure(username, ip);
        }
    }

    @Test
    void failuresBelowThresholdDoNotLock() {
        fail(USER, IP, AdminLoginThrottleService.MAX_FAILURES_PER_USERNAME - 1);
        assertTrue(throttle.lockRemaining(USER, IP).isEmpty());
    }

    @Test
    void usernameThresholdLocksThatUsernameOnly() {
        fail(USER, IP, AdminLoginThrottleService.MAX_FAILURES_PER_USERNAME);

        long remaining = throttle.lockRemaining(USER, OTHER_IP).orElse(0L);
        assertTrue(remaining > 0, "达到阈值的用户名应被锁定");
        assertTrue(remaining <= AdminLoginThrottleService.LOCK_SECONDS);

        assertTrue(throttle.lockRemaining(OTHER_USER, IP).isEmpty(),
                "用户名维度被锁不应牵连其它用户名");
    }

    @Test
    void unknownUsernamesAreCountedAndLockedIdentically() {
        // 不存在的用户名同样累计并触发锁定：锁定只看请求次数，不暴露账号存在性
        fail(OTHER_USER, OTHER_IP, AdminLoginThrottleService.MAX_FAILURES_PER_USERNAME);
        assertTrue(throttle.lockRemaining(OTHER_USER, OTHER_IP).isPresent());
    }

    @Test
    void sourceThresholdLocksThatSourceEvenWhenEveryUsernameDiffers() {
        for (int i = 0; i < AdminLoginThrottleService.MAX_FAILURES_PER_SOURCE; i++) {
            throttle.recordFailure("random_user_" + i, IP);
        }

        assertTrue(throttle.lockRemaining("anything", IP).isPresent(), "同一来源的失败累计应锁定该来源");
        assertTrue(throttle.lockRemaining("anything", OTHER_IP).isEmpty(), "其它来源不受影响");
    }

    @Test
    void successfulLoginClearsUsernameCounterButNotSourceCounter() {
        fail(USER, IP, AdminLoginThrottleService.MAX_FAILURES_PER_USERNAME - 1);
        throttle.clearFailures(USER);
        // 计数已清零：再失败一次也不会到达阈值
        fail(USER, IP, 1);
        assertTrue(throttle.lockRemaining(USER, IP).isEmpty());

        // 成功登录不能洗白爆破来源：把来源推到阈值后，clearFailures 之后依然锁定
        for (int i = 0; i < AdminLoginThrottleService.MAX_FAILURES_PER_SOURCE; i++) {
            throttle.recordFailure("random_user_" + i, IP);
        }
        throttle.clearFailures("random_user_0");
        assertTrue(throttle.lockRemaining("random_user_0", IP).isPresent(),
                "来源维度锁定不应被成功登录清除");
    }

    @Test
    void lockExpiresWithTime() {
        fail(USER, IP, AdminLoginThrottleService.MAX_FAILURES_PER_USERNAME);
        assertTrue(throttle.lockRemaining(USER, IP).isPresent());

        store.advanceSeconds(AdminLoginThrottleService.LOCK_SECONDS + 1L);
        assertFalse(throttle.lockRemaining(USER, IP).isPresent(), "锁定到期后应自动放行");
    }

    @Test
    void storageFailureFailsOpen() {
        store.setDown(true);
        fail(USER, IP, AdminLoginThrottleService.MAX_FAILURES_PER_USERNAME * 2);
        assertTrue(throttle.lockRemaining(USER, IP).isEmpty(), "存储不可用时应放行，不能把登录整条链路打死");
    }

    @Test
    void countedKeysAreScopedByDimension() {
        fail(USER, IP, 1);
        // 用户名与来源各占一个键，互不覆盖
        assertEquals(2, store.size());
    }
}
