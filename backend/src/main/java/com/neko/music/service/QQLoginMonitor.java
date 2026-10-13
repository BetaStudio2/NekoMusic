package com.neko.music.service;

import com.neko.music.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * QQ 音乐登录掉线告警：与 {@link NeteaseLoginMonitor} 同构——Cookie 已配置、登录态由「在线」变为
 * 「掉线」时经 {@link NotificationService} 推送一次 hook 告警，恢复登录后自动复位，不重复刷屏。
 *
 * <p>事件驱动，不做定时巡检：QQ 补全「有候选但全部未能入库」时会调用
 * {@link QQMusicClient#probeLoginState()} 做三态探测并上报。查询失败（网络 / 接口异常）视为「未知」，
 * 既不告警也不复位，避免网络抖动被误判为掉线。Cookie 失效后 QQ 会退化为游客态，付费曲静默
 * NOT_FOUND，正是需要告警的场景。</p>
 */
public class QQLoginMonitor {

    private static final Logger logger = LoggerFactory.getLogger(QQLoginMonitor.class);

    /** 掉线告警文案：说明影响面与恢复方式。 */
    static final String OFFLINE_REASON =
            "Cookie 已失效（异地登录 / 风控 / 改密都会导致掉线）。"
                    + "QQ 音乐补全会退化为游客态、付费曲静默取不到，"
                    + "请到后台「系统设置 · 外站补全」重新扫码登录。";

    private final ConfigManager configManager;
    private final QQMusicClient qqClient;
    private final NotificationService notificationService;

    /** 上一次已知的登录态；{@code null} 表示尚未确定。 */
    private Boolean lastLoggedIn;
    /** 本进程是否已完成过一次有效探测（用于覆盖「启动时 Cookie 就已是失效状态」的情况）。 */
    private boolean probedOnce;

    public QQLoginMonitor(ConfigManager configManager,
                          QQMusicClient qqClient,
                          NotificationService notificationService) {
        this.configManager = configManager;
        this.qqClient = qqClient;
        this.notificationService = notificationService;
    }

    /**
     * 事件驱动入口：由真实请求结果上报登录态。
     *
     * <p>仅在「上次在线、本次掉线」或「本进程首次有效探测即掉线」时推送一次 hook；
     * 未配置 Cookie 时复位状态且不告警。</p>
     */
    public synchronized void reportLoginState(boolean loggedIn) {
        if (configManager.getQqCookie().isEmpty()) {
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
            notificationService.scheduleQqLoginOfflineNotification(OFFLINE_REASON);
            logger.warn("检测到 QQ 音乐登录掉线，已推送 hook 告警");
        }
    }

    /**
     * 按需主动探测一次并上报（后台状态页等场景）。
     *
     * <p>查询失败（网络 / 接口异常）不判为掉线，也不复位状态。</p>
     */
    public synchronized void checkOnce() {
        if (configManager.getQqCookie().isEmpty()) {
            lastLoggedIn = null;
            probedOnce = false;
            return;
        }
        Optional<Boolean> probed = qqClient.probeLoginState();
        if (probed.isEmpty()) {
            return;
        }
        reportLoginState(probed.get());
    }
}
