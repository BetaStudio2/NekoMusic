package com.neko.music.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neko.music.config.ConfigManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 网易云登录掉线告警：告警触发时机与去重（事件驱动 / 按需探测）。
 *
 * <p>只覆盖纯状态机，不发真实网络请求：假客户端提供三态登录态，假通知服务只计数。</p>
 */
class NeteaseLoginMonitorTest {

    /** 假客户端：登录态可控（empty 表示「查询失败」，覆盖网络抖动）。 */
    private static class StubNeteaseClient extends NeteaseCloudMusicClient {
        Optional<Boolean> probe = Optional.of(false);

        StubNeteaseClient(ConfigManager config, ObjectMapper mapper) {
            super(config, mapper);
        }

        @Override
        public Optional<Boolean> probeLoginState() {
            return probe;
        }
    }

    /** 假通知服务：只记录掉线告警次数，避免真的打 webhook。 */
    private static class StubNotificationService extends NotificationService {
        int offlineAlerts;

        StubNotificationService(ConfigManager config) {
            super(config);
        }

        @Override
        public void scheduleNeteaseLoginOfflineNotification(String reason) {
            offlineAlerts++;
        }
    }

    private static ConfigManager configWithCookie(String cookie) {
        ConfigManager config = new ConfigManager();
        config.applyOverrides(Map.of("netease_search_fill.cookie", cookie));
        return config;
    }

    private static NeteaseLoginMonitor monitor(ConfigManager config, StubNeteaseClient client,
                                               StubNotificationService notifier) {
        return new NeteaseLoginMonitor(config, client, notifier);
    }

    @Test
    @DisplayName("巡检：启动时已掉线告警一次，持续掉线不重复")
    void alertsOnceOnFirstOfflineProbe() {
        ConfigManager config = configWithCookie("MUSIC_U=abc");
        StubNeteaseClient client = new StubNeteaseClient(config, new ObjectMapper());
        StubNotificationService notifier = new StubNotificationService(config);
        NeteaseLoginMonitor monitor = monitor(config, client, notifier);

        monitor.checkOnce();
        assertEquals(1, notifier.offlineAlerts, "配置了 Cookie 却已掉线，首次巡检应告警一次");

        monitor.checkOnce();
        assertEquals(1, notifier.offlineAlerts, "持续掉线不应重复告警");
    }

    @Test
    @DisplayName("巡检：仅「上次在线 → 本次掉线」告警，恢复后再掉线再告警一次")
    void alertsOnlyOnTransitionToOffline() {
        ConfigManager config = configWithCookie("MUSIC_U=abc");
        StubNeteaseClient client = new StubNeteaseClient(config, new ObjectMapper());
        StubNotificationService notifier = new StubNotificationService(config);
        NeteaseLoginMonitor monitor = monitor(config, client, notifier);

        client.probe = Optional.of(true);
        monitor.checkOnce();
        assertEquals(0, notifier.offlineAlerts, "在线时不应告警");

        client.probe = Optional.of(false);
        monitor.checkOnce();
        assertEquals(1, notifier.offlineAlerts, "掉线应推送一次");

        monitor.checkOnce();
        assertEquals(1, notifier.offlineAlerts, "持续掉线不应重复告警");

        client.probe = Optional.of(true);
        monitor.checkOnce();
        client.probe = Optional.of(false);
        monitor.checkOnce();
        assertEquals(2, notifier.offlineAlerts, "恢复登录后再次掉线应再告警一次");
    }

    @Test
    @DisplayName("巡检：查询失败不算掉线，不误报也不复位状态")
    void queryFailureIsNotOffline() {
        ConfigManager config = configWithCookie("MUSIC_U=abc");
        StubNeteaseClient client = new StubNeteaseClient(config, new ObjectMapper());
        StubNotificationService notifier = new StubNotificationService(config);
        NeteaseLoginMonitor monitor = monitor(config, client, notifier);

        client.probe = Optional.of(true);
        monitor.checkOnce();

        client.probe = Optional.empty();
        monitor.checkOnce();
        assertEquals(0, notifier.offlineAlerts, "查询失败不应误报掉线");

        client.probe = Optional.of(false);
        monitor.checkOnce();
        assertEquals(1, notifier.offlineAlerts, "查询失败后仍保留在线状态，真掉线应告警");
    }

    @Test
    @DisplayName("事件驱动：reportLoginState 仅在 在线→掉线 跳变时告警一次")
    void reportLoginStateAlertsOnlyOnTransition() {
        ConfigManager config = configWithCookie("MUSIC_U=abc");
        StubNeteaseClient client = new StubNeteaseClient(config, new ObjectMapper());
        StubNotificationService notifier = new StubNotificationService(config);
        NeteaseLoginMonitor monitor = monitor(config, client, notifier);

        monitor.reportLoginState(true);
        assertEquals(0, notifier.offlineAlerts, "在线时不应告警");

        monitor.reportLoginState(false);
        assertEquals(1, notifier.offlineAlerts, "在线→掉线应告警一次");

        monitor.reportLoginState(false);
        assertEquals(1, notifier.offlineAlerts, "持续掉线不应重复告警");

        monitor.reportLoginState(true);
        monitor.reportLoginState(false);
        assertEquals(2, notifier.offlineAlerts, "恢复后再掉线应再告警一次");
    }

    @Test
    @DisplayName("未配置 Cookie 时不做掉线告警")
    void noAlertWithoutCookie() {
        ConfigManager config = new ConfigManager();
        StubNeteaseClient client = new StubNeteaseClient(config, new ObjectMapper());
        StubNotificationService notifier = new StubNotificationService(config);
        NeteaseLoginMonitor monitor = monitor(config, client, notifier);

        client.probe = Optional.of(false);
        monitor.checkOnce();
        assertEquals(0, notifier.offlineAlerts);
    }
}
