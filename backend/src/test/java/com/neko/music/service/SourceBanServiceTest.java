package com.neko.music.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 认证失败来源封禁测试：阈值触发的档位、递增退避与封顶、安静期回退、到期自动解封，以及存储故障放行。
 *
 * <p>封禁只统计<b>真实认证失败</b>，所以成功登录要能清掉窗口内的失败计数（但不能把递增退避清零，
 * 否则「爆破—插一次成功—再爆破」就能把处罚洗白）。</p>
 */
class SourceBanServiceTest {

    private static final String IP = "203.0.113.7";
    private static final String OTHER_IP = "198.51.100.9";

    private TestSourceBanStore store;
    private SourceBanService ban;

    @BeforeEach
    void setUp() {
        store = new TestSourceBanStore();
        ban = new SourceBanService(store);
    }

    /** 连续记录 {@code times} 次认证失败，返回最后一次的封禁触发结果。 */
    private long fail(String ip, int times) {
        long last = 0;
        for (int i = 0; i < times; i++) {
            last = ban.recordAuthFailure(ip);
        }
        return last;
    }

    @Test
    void failuresBelowThresholdDoNotBan() {
        assertEquals(0, fail(IP, SourceBanService.FAILURES_BEFORE_BAN - 1));
        assertFalse(ban.isBanned(IP));
        assertEquals(0, ban.remainingBanSeconds(IP));
    }

    @Test
    void thresholdTriggersFirstLadderStep() {
        long duration = fail(IP, SourceBanService.FAILURES_BEFORE_BAN);
        assertEquals(SourceBanService.BAN_LADDER_SECONDS[0], duration);
        assertTrue(ban.isBanned(IP));
        assertEquals(duration, ban.remainingBanSeconds(IP));
    }

    @Test
    void repeatOffenderEscalatesAlongLadderAndCapsAtLastStep() {
        int[] ladder = SourceBanService.BAN_LADDER_SECONDS;

        for (int step = 0; step < ladder.length; step++) {
            // 每一轮先把上一轮的失败计数窗口作废（触发时已清空），再攒满阈值
            assertEquals(ladder[step], fail(IP, SourceBanService.FAILURES_BEFORE_BAN),
                    "第 " + (step + 1) + " 次应落在退避阶梯第 " + (step + 1) + " 档");
        }

        // 超过阶梯长度后封顶，不再无限加长
        assertEquals(ladder[ladder.length - 1], fail(IP, SourceBanService.FAILURES_BEFORE_BAN),
                "超过阶梯长度应封顶在最后一档");
    }

    @Test
    void quietPeriodResetsEscalation() {
        fail(IP, SourceBanService.FAILURES_BEFORE_BAN);

        // 距上次触发超过安静期：退避档位与封禁一起过期，下一轮从第一档重新开始
        store.advanceSeconds(SourceBanService.LEVEL_QUIET_SECONDS + 1L);
        assertFalse(ban.isBanned(IP));

        assertEquals(SourceBanService.BAN_LADDER_SECONDS[0], fail(IP, SourceBanService.FAILURES_BEFORE_BAN),
                "安静期过后应从第一档重新开始");
    }

    @Test
    void banExpiresWithTime() {
        long duration = fail(IP, SourceBanService.FAILURES_BEFORE_BAN);
        store.advanceSeconds(duration - 1);
        assertTrue(ban.isBanned(IP), "封禁到期前仍应处于封禁状态");

        store.advanceSeconds(1);
        assertFalse(ban.isBanned(IP), "封禁到期后应自动解封");
    }

    @Test
    void authSuccessClearsStrikesWithinWindow() {
        fail(IP, SourceBanService.FAILURES_BEFORE_BAN - 1);
        ban.recordAuthSuccess(IP);

        // 计数被清空：再攒接近阈值也不会触发（若没清空，这里累计已超过阈值）
        assertEquals(0, fail(IP, SourceBanService.FAILURES_BEFORE_BAN - 1));
        assertFalse(ban.isBanned(IP));
    }

    @Test
    void authSuccessDoesNotResetEscalationLevel() {
        fail(IP, SourceBanService.FAILURES_BEFORE_BAN);

        // 插一次成功登录洗白失败计数，但递增退避档位保留：下次复发仍应更重
        ban.recordAuthSuccess(IP);
        assertEquals(SourceBanService.BAN_LADDER_SECONDS[1], fail(IP, SourceBanService.FAILURES_BEFORE_BAN),
                "成功登录不应把递增退避清零");
    }

    @Test
    void distinctSourcesAreIndependent() {
        fail(IP, SourceBanService.FAILURES_BEFORE_BAN);

        assertTrue(ban.isBanned(IP));
        assertFalse(ban.isBanned(OTHER_IP), "封禁不应牵连其它来源");
        assertEquals(0, fail(OTHER_IP, SourceBanService.FAILURES_BEFORE_BAN - 1));
    }

    @Test
    void storageFailureFailsOpen() {
        store.setDown(true);

        for (int i = 0; i < SourceBanService.FAILURES_BEFORE_BAN * 3; i++) {
            assertEquals(0, ban.recordAuthFailure(IP));
        }
        assertFalse(ban.isBanned(IP), "存储不可用时不应封禁，也不能把认证链路打死");
    }

    @Test
    void banTriggersAlertWithScope() {
        List<String> alerts = new ArrayList<>();
        SourceBanService withAlert = new SourceBanService(store, (ip, duration, scope) ->
                alerts.add(ip + "|" + duration + "|" + scope));

        for (int i = 0; i < SourceBanService.FAILURES_BEFORE_BAN; i++) {
            withAlert.recordAuthFailure(IP, "user-login");
        }

        assertEquals(1, alerts.size(), "触发封禁应告警一次");
        assertEquals(IP + "|" + SourceBanService.BAN_LADDER_SECONDS[0] + "|user-login", alerts.get(0));
    }

    @Test
    void alertIsCoalescedWithinCooldown() {
        AtomicLong clock = new AtomicLong(1_000L);
        AtomicInteger alerts = new AtomicInteger();
        SourceBanService withAlert = new SourceBanService(store,
                (ip, duration, scope) -> alerts.incrementAndGet(), clock::get);

        for (int i = 0; i < SourceBanService.FAILURES_BEFORE_BAN; i++) {
            withAlert.recordAuthFailure(IP, "login");
        }
        assertEquals(1, alerts.get());

        // 冷却期内升级封禁也不重复告警：避免攻击者用海量来源刷爆告警通道
        for (int i = 0; i < SourceBanService.FAILURES_BEFORE_BAN; i++) {
            withAlert.recordAuthFailure(IP, "login");
        }
        assertEquals(1, alerts.get(), "同一来源冷却期内不应重复告警");

        // 越过告警冷却后应再次告警
        clock.addAndGet(SourceBanService.ALERT_COOLDOWN_SECONDS + 1L);
        for (int i = 0; i < SourceBanService.FAILURES_BEFORE_BAN; i++) {
            withAlert.recordAuthFailure(IP, "login");
        }
        assertEquals(2, alerts.get());
    }

    @Test
    void alertFailureDoesNotBreakBan() {
        SourceBanService withAlert = new SourceBanService(store, (ip, duration, scope) -> {
            throw new RuntimeException("告警通道故障");
        });

        for (int i = 0; i < SourceBanService.FAILURES_BEFORE_BAN; i++) {
            withAlert.recordAuthFailure(IP, "login");
        }
        assertTrue(withAlert.isBanned(IP), "告警异常不应影响封禁本身");
    }
}
