package com.neko.music.service;

import com.neko.music.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * 网易云登录掉线告警：Cookie 已配置、登录态由「在线」变为「掉线」时，通过
 * {@link NotificationService} 推送一次 hook 告警；恢复登录后自动复位，不重复刷屏。
 *
 * <p>采用**事件驱动**，不做定时巡检：真实依赖登录态的操作（搜索补全等）失败时会调用
 * {@link NeteaseCloudMusicClient#probeLoginState()} 做三态探测，并把结果经
 * {@link #reportLoginState(boolean)} 上报。查询失败（网络 / 接口异常）视为「未知」，
 * 既不告警也不复位，避免网络抖动被误判为掉线。</p>
 */
public class NeteaseLoginMonitor {

    private static final Logger logger = LoggerFactory.getLogger(NeteaseLoginMonitor.class);

    /** 掉线告警文案：说明影响面与恢复方式。 */
    static final String OFFLINE_REASON =
            "Cookie 已失效（异地登录 / 平台风控 / 改密都会导致掉线）。"
                    + "网易云搜索补全等依赖登录态的功能将不可用，请到后台「系统设置 · 网易云补全」重新扫码登录。";

    private final ConfigManager configManager;
    private final NeteaseCloudMusicClient neteaseClient;
    private final NotificationService notificationService;

    /** 上一次已知的登录态；{@code null} 表示尚未确定。 */
    private Boolean lastLoggedIn;
    /** 本进程是否已完成过一次有效探测（用于覆盖「启动时 Cookie 就已是失效状态」的情况）。 */
    private boolean probedOnce;

    public NeteaseLoginMonitor(ConfigManager configManager,
                               NeteaseCloudMusicClient neteaseClient,
                               NotificationService notificationService) {
        this.configManager = configManager;
        this.neteaseClient = neteaseClient;
        this.notificationService = notificationService;
    }

    /**
     * 事件驱动入口：由真实请求结果上报登录态。
     *
     * <p>仅在「上次在线、本次掉线」或「本进程首次有效探测即掉线」时推送一次 hook；
     * 未配置 Cookie 时复位状态且不告警。</p>
     */
    public synchronized void reportLoginState(boolean loggedIn) {
        if (configManager.getNeteaseCookie().isEmpty()) {
            lastLoggedIn = null;
            probedOnce = false;
            return;
        }
        boolean previouslyOnline = Boolean.TRUE.equals(lastLoggedIn);
        boolean firstProbe = !probedOnce;
        probedOnce = true;
        lastLoggedIn = loggedIn;
        if (loggedIn) {
            return;
        }
        if (previouslyOnline || firstProbe) {
            notificationService.scheduleNeteaseLoginOfflineNotification(OFFLINE_REASON);
            logger.warn("检测到网易云登录掉线，已推送 hook 告警");
        }
    }

    /**
     * 按需主动探测一次并上报（后台状态页等场景）。
     *
     * <p>查询失败（网络 / 接口异常）不判为掉线，也不复位状态。</p>
     */
    public synchronized void checkOnce() {
        if (configManager.getNeteaseCookie().isEmpty()) {
            lastLoggedIn = null;
            probedOnce = false;
            return;
        }
        Optional<Boolean> probed = neteaseClient.probeLoginState();
        if (probed.isEmpty()) {
            return;
        }
        reportLoginState(probed.get());
    }
}
