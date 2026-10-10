package com.neko.music.service;

import com.neko.music.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 网易云登录掉线巡检：Cookie 已配置、登录态由「在线」变为「掉线」时，通过
 * {@link NotificationService} 推送一次 hook 告警；恢复登录后自动复位，不重复刷屏。
 *
 * <p>巡检用 {@link NeteaseCloudMusicClient#probeLoginState()} 做三态判断：查询失败（网络 / 接口异常）
 * 视为「未知」，既不告警也不复位，避免网络抖动被误判为掉线。间隔写死为类常量，不进后台设置清单。</p>
 */
public class NeteaseLoginMonitor {

    private static final Logger logger = LoggerFactory.getLogger(NeteaseLoginMonitor.class);

    /** 启动后多久开始第一次检查，以及之后每隔多久检查一次。 */
    static final long INITIAL_DELAY_SECONDS = 120;
    static final long PERIOD_SECONDS = 300;

    /** 掉线告警文案：说明影响面与恢复方式。 */
    static final String OFFLINE_REASON =
            "Cookie 已失效（异地登录 / 平台风控 / 改密都会导致掉线）。"
                    + "网易云搜索补全等依赖登录态的功能将不可用，请到后台「系统设置 · 网易云补全」重新扫码登录。";

    private final ConfigManager configManager;
    private final NeteaseCloudMusicClient neteaseClient;
    private final NotificationService notificationService;

    /** 上一次探测到的登录态；{@code null} 表示尚未确定。 */
    private Boolean lastLoggedIn;
    /** 本进程是否已完成过一次有效探测（用于覆盖「启动时 Cookie 就已是失效状态」的情况）。 */
    private boolean probedOnce;
    private ScheduledExecutorService scheduler;

    public NeteaseLoginMonitor(ConfigManager configManager,
                               NeteaseCloudMusicClient neteaseClient,
                               NotificationService notificationService) {
        this.configManager = configManager;
        this.neteaseClient = neteaseClient;
        this.notificationService = notificationService;
    }

    /** 启动巡检（守护线程，随进程退出）。重复调用无副作用。 */
    public synchronized void start() {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "netease-login-monitor");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::checkOnceSafely,
                INITIAL_DELAY_SECONDS, PERIOD_SECONDS, TimeUnit.SECONDS);
        logger.info("网易云登录掉线巡检已启动：每 {} 秒检查一次", PERIOD_SECONDS);
    }

    public synchronized void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private void checkOnceSafely() {
        try {
            checkOnce();
        } catch (Exception e) {
            logger.warn("网易云登录状态巡检异常: {}", e.getMessage());
        }
    }

    /**
     * 巡检一次：Cookie 未配置则视为未登录且不告警；查询失败保持上次状态；
     * 仅在「上次在线、本次掉线」或「本进程首次探测即掉线」时推送一次 hook。
     */
    synchronized void checkOnce() {
        if (configManager.getNeteaseCookie().isEmpty()) {
            lastLoggedIn = null;
            probedOnce = false;
            return;
        }
        Optional<Boolean> probed = neteaseClient.probeLoginState();
        if (probed.isEmpty()) {
            // 查询失败（网络 / 接口异常）不属于掉线：保持上次状态，不告警也不复位
            return;
        }
        boolean loggedIn = probed.get();
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
}
