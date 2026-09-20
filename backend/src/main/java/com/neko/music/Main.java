package com.neko.music;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.neko.music.config.ConfigManager;
import com.neko.music.database.AdminDatabaseManager;
import com.neko.music.database.DatabaseManager;
import com.neko.music.database.DatabaseInitializer;
import com.neko.music.database.LyricsDatabaseManager;
import com.neko.music.service.VideoRenderJobStore;
import com.neko.music.database.VipPayOrderDatabaseManager;
import com.neko.music.database.VipPricingDatabaseManager;
import com.neko.music.handlers.*;

import com.neko.music.service.AdminAuthService;
import com.neko.music.service.EmailService;
import com.neko.music.service.DailyRecommendationService;
import com.neko.music.service.LyricsSearchIndex;
import com.neko.music.service.NotificationService;
import com.neko.music.service.PlaylistService;
import com.neko.music.service.RedisService;
import com.neko.music.service.QrLoginService;
import com.neko.music.service.RedisTokenStore;
import com.neko.music.service.UserAuthService;
import com.neko.music.service.VerificationCodeRateLimitService;
import com.neko.music.service.IPRateLimitService;
import com.neko.music.service.SliderCaptchaService;
import com.neko.music.service.VideoRenderArtifactCleanup;
import com.neko.music.service.VideoRenderQuotaService;
import com.neko.music.service.AdminMusicIngestService;
import com.neko.music.service.NeteaseCloudMusicClient;
import com.neko.music.service.AppReleaseService;
import com.neko.music.service.NeteaseSearchFillService;
import com.neko.music.service.ExternalImportService;
import com.neko.music.service.QQMusicClient;
import com.neko.music.service.KugouMusicClient;
import com.neko.music.service.QishuiMusicClient;
import com.neko.music.service.MusicRecognitionService;
import com.neko.music.service.VideoRenderService;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import com.neko.music.filter.IPRateLimitFilter;
import com.neko.music.util.ClientReleaseStorage;
import com.neko.music.util.SiteResourceStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.DispatcherType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {
    static {
        // 在类加载时尽早设置字符编码
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("sun.jnu.encoding", "UTF-8");
        System.setProperty("sun.stdout.encoding", "UTF-8");
        System.setProperty("sun.stderr.encoding", "UTF-8");
    }
    
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Gson gson = new Gson();

    private static Server server;
    private static DatabaseManager databaseManager;
    private static ConfigManager configManager;
    private static AdminDatabaseManager adminDatabaseManager;
    private static AdminAuthService adminAuthService;
    private static UserAuthService userAuthService;
    private static QrLoginService qrLoginService;
    private static EmailService emailService;
    private static RedisService redisService;
    private static PlaylistService playlistService;
    private static NotificationService notificationService;
    private static IPRateLimitService ipRateLimitService;
    private static VipPricingDatabaseManager vipPricingDatabaseManager;
    private static VipPayOrderDatabaseManager vipPayOrderDatabaseManager;
    private static VideoRenderJobStore videoRenderJobStore;
    private static VideoRenderQuotaService videoRenderQuotaService;
    private static VideoRenderService videoRenderService;
    private static SliderCaptchaService sliderCaptchaService;
    private static AdminMusicIngestService adminMusicIngestService;
    private static NeteaseCloudMusicClient neteaseCloudMusicClient;
    private static NeteaseSearchFillService neteaseSearchFillService;
    private static ExternalImportService externalImportService;
    private static QQMusicClient qqMusicClient;
    private static KugouMusicClient kugouMusicClient;
    private static QishuiMusicClient qishuiMusicClient;
    private static AppReleaseService appReleaseService;
    private static DailyRecommendationService dailyRecommendationService;
    private static LyricsSearchIndex lyricsSearchIndex;
    private static LyricsDatabaseManager lyricsDatabaseManager;
    private static MusicRecognitionService musicRecognitionService;
    private static ScheduledExecutorService dailyRecommendationScheduler;

    public static void main(String[] args) throws Exception {
        // 设置JVM默认时区为中国标准时间（UTC+8）
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Shanghai"));

        // 重新设置System.out和System.err的编码
        System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));
        System.setErr(new java.io.PrintStream(System.err, true, "UTF-8"));

        logger.info("正在启动NekoMusic音乐平台...");
        
        // 初始化配置管理器
        configManager = new ConfigManager();
        configManager.loadConfig();

        try {
            ClientReleaseStorage.ensureStorageDir();
            logger.info("客户端安装包目录: {}", ClientReleaseStorage.storageDir().toAbsolutePath());
        } catch (Exception e) {
            logger.warn("创建客户端安装包目录失败: {}", e.getMessage());
        }

        try {
            SiteResourceStorage.ensureStorageDir();
            logger.info("前端站点目录: {}", SiteResourceStorage.storageDir());
        } catch (Exception e) {
            logger.warn("创建前端站点目录失败: {}", e.getMessage());
        }
        
        // 初始化数据库管理器
        databaseManager = new DatabaseManager(configManager);
        databaseManager.init();
        lyricsDatabaseManager = new LyricsDatabaseManager(databaseManager);
        
        // 初始化数据库表
        DatabaseInitializer.initializeTables(databaseManager);

        appReleaseService = new AppReleaseService();

        vipPricingDatabaseManager = new VipPricingDatabaseManager(databaseManager);
        vipPayOrderDatabaseManager = new VipPayOrderDatabaseManager(databaseManager);

        // 初始化Redis服务（视频配额依赖 Redis，须在 video 服务之前）
        redisService = new RedisService(configManager);
        ipRateLimitService = new IPRateLimitService(configManager, redisService);
        RedisTokenStore tokenStore = new RedisTokenStore(redisService);

        VideoRenderArtifactCleanup videoRenderArtifactCleanup =
                new VideoRenderArtifactCleanup(configManager.getVideoRenderArtifactRetentionHours());
        videoRenderJobStore = new VideoRenderJobStore(
                redisService, objectMapper, configManager.getVideoRenderArtifactRetentionHours());
        videoRenderQuotaService = new VideoRenderQuotaService(configManager, redisService);
        videoRenderService = new VideoRenderService(configManager, videoRenderJobStore, videoRenderArtifactCleanup);
        Runtime.getRuntime().addShutdownHook(new Thread(videoRenderService::shutdown, "video-render-shutdown"));

        // 初始化管理员数据库管理器和认证服务
        adminDatabaseManager = new AdminDatabaseManager(databaseManager);
        adminAuthService = new AdminAuthService(adminDatabaseManager, tokenStore);
        
        // 初始化邮件服务
        emailService = new EmailService(configManager);

        VerificationCodeRateLimitService verificationCodeRateLimitService =
                new VerificationCodeRateLimitService(configManager, redisService);

        // 初始化用户认证服务
        userAuthService = new UserAuthService(
                databaseManager, configManager, emailService, redisService, tokenStore,
                verificationCodeRateLimitService);

        // 扫码登录会话（Redis 短 TTL，PC 轮询取 token）
        qrLoginService = new QrLoginService(redisService, objectMapper);

        sliderCaptchaService = new SliderCaptchaService();

        adminMusicIngestService = new AdminMusicIngestService();
        neteaseCloudMusicClient = new NeteaseCloudMusicClient(configManager, objectMapper);
        neteaseSearchFillService = new NeteaseSearchFillService(
                configManager,
                neteaseCloudMusicClient,
                adminMusicIngestService,
                redisService);
        Runtime.getRuntime().addShutdownHook(new Thread(neteaseSearchFillService::shutdown, "netease-fill-shutdown"));
        qqMusicClient = new QQMusicClient(objectMapper);
        kugouMusicClient = new KugouMusicClient(objectMapper);
        qishuiMusicClient = new QishuiMusicClient(objectMapper);
        dailyRecommendationService = new DailyRecommendationService(
                databaseManager, redisService, configManager, objectMapper);
        startDailyRecommendationScheduler();

        lyricsSearchIndex = new LyricsSearchIndex();
        lyricsSearchIndex.buildIndexAsync();

        musicRecognitionService = new MusicRecognitionService(databaseManager, configManager);
        musicRecognitionService.warmUp();
        Runtime.getRuntime().addShutdownHook(new Thread(musicRecognitionService::close, "music-recognition-shutdown"));

        // 初始化歌单服务
        playlistService = new PlaylistService(databaseManager);
        externalImportService = new ExternalImportService(
                neteaseCloudMusicClient,
                neteaseSearchFillService,
                adminMusicIngestService,
                playlistService,
                qqMusicClient,
                kugouMusicClient);
        Runtime.getRuntime().addShutdownHook(new Thread(externalImportService::shutdown, "external-import-shutdown"));
        
        // 初始化通知服务
        notificationService = new NotificationService(configManager);
        
        // 创建默认管理员账号（如果不存在）
        createDefaultAdminIfNotExists();

        // 确保至少有一个超级管理员
        ensureSuperAdminExists();
        
        QueuedThreadPool threadPool = new QueuedThreadPool();
        threadPool.setName("jetty-worker");
        threadPool.setMinThreads(configManager.getJettyMinThreads());
        threadPool.setMaxThreads(configManager.getJettyMaxThreads());
        threadPool.setIdleTimeout((int) Math.min(Integer.MAX_VALUE, configManager.getJettyIdleTimeoutMs()));

        server = new Server(threadPool);
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(configManager.getPort());
        server.addConnector(connector);
        logger.info("Jetty 线程池: minThreads={}, maxThreads={}, idleTimeoutMs={}, listenPort={}",
                configManager.getJettyMinThreads(), configManager.getJettyMaxThreads(),
                configManager.getJettyIdleTimeoutMs(), configManager.getPort());
        
        // 创建上下文处理器
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);

        // 注册服务到 ServletContext，供 Filter 使用
        context.setAttribute("configManager", configManager);
        context.setAttribute("ipRateLimitService", ipRateLimitService);

        // IP 限流需在嵌入式 Jetty 中显式注册（@WebFilter 不会生效）
        context.addFilter(IPRateLimitFilter.class, "/*", EnumSet.allOf(DispatcherType.class));

        // 前端静态资源 + 全部 API/页面路由（详见 ServletRegistrar）
        ServletRegistrar.register(context, configManager);
        // 启动服务器
        server.start();
        logger.info("NekoMusic服务器已在端口{}启动", configManager.getPort());
        server.join();
    }
    
    private static void createDefaultAdminIfNotExists() {
        String defaultUsername = "admin";
        String defaultPassword = "admin";

        // 检查管理员表是否为空，仅在首次初始化时创建默认管理员
        if (adminDatabaseManager.getAllAdmins().isEmpty()) {
            boolean created = adminAuthService.createAdmin(defaultUsername, defaultPassword, "admin@nekomusic.com");
            if (created) {
                logger.info("默认管理员账号已创建: {}/{}", defaultUsername, defaultPassword);
                // 将默认管理员设置为超级管理员
                try (Connection conn = databaseManager.getConnection();
                     PreparedStatement stmt = conn.prepareStatement("UPDATE admins SET role = 'super_admin' WHERE username = ?")) {
                    stmt.setString(1, defaultUsername);
                    stmt.executeUpdate();
                    logger.info("已将默认管理员设置为超级管理员");
                } catch (Exception e) {
                    logger.error("设置默认管理员角色失败", e);
                }
            } else {
                logger.error("创建默认管理员账号失败");
            }
        } else {
            logger.info("管理员表中已有数据，跳过创建默认管理员账号");
        }
    }

    private static void ensureSuperAdminExists() {
        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM admins WHERE role = 'super_admin'");
            if (rs.next() && rs.getInt(1) == 0) {
                // 没有超级管理员，将第一个管理员设置为super_admin
                int updated = stmt.executeUpdate("UPDATE admins SET role = 'super_admin' WHERE id = (SELECT MIN(id) FROM admins)");
                if (updated > 0) {
                    logger.info("已将第一个管理员设置为超级管理员");
                }
            }
        } catch (Exception e) {
            logger.error("确保超级管理员存在时出错", e);
        }
    }
    
    public static DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    public static ConfigManager getConfigManager() {
        return configManager;
    }
    
    public static AdminDatabaseManager getAdminDatabaseManager() {
        return adminDatabaseManager;
    }
    
    public static AdminAuthService getAdminAuthService() {
        return adminAuthService;
    }
    
    public static UserAuthService getUserAuthService() {
        return userAuthService;
    }

    public static QrLoginService getQrLoginService() {
        return qrLoginService;
    }

    public static SliderCaptchaService getSliderCaptchaService() {
        return sliderCaptchaService;
    }

    public static AdminMusicIngestService getAdminMusicIngestService() {
        return adminMusicIngestService;
    }

    public static NeteaseSearchFillService getNeteaseSearchFillService() {
        return neteaseSearchFillService;
    }

    public static ExternalImportService getExternalImportService() {
        return externalImportService;
    }

    public static QQMusicClient getQQMusicClient() {
        return qqMusicClient;
    }

    public static KugouMusicClient getKugouMusicClient() {
        return kugouMusicClient;
    }

    public static QishuiMusicClient getQishuiMusicClient() {
        return qishuiMusicClient;
    }

    public static NeteaseCloudMusicClient getNeteaseCloudMusicClient() {
        return neteaseCloudMusicClient;
    }

    public static AppReleaseService getAppReleaseService() {
        return appReleaseService;
    }

    public static DailyRecommendationService getDailyRecommendationService() {
        return dailyRecommendationService;
    }

    public static LyricsSearchIndex getLyricsSearchIndex() {
        return lyricsSearchIndex;
    }

    public static LyricsDatabaseManager getLyricsDatabaseManager() {
        return lyricsDatabaseManager;
    }

    public static MusicRecognitionService getMusicRecognitionService() {
        return musicRecognitionService;
    }
    
    public static EmailService getEmailService() {
        return emailService;
    }
    
    public static RedisService getRedisService() {
        return redisService;
    }

    public static IPRateLimitService getIPRateLimitService() {
        return ipRateLimitService;
    }

    public static PlaylistService getPlaylistService() {
        return playlistService;
    }

    public static VipPricingDatabaseManager getVipPricingDatabaseManager() {
        return vipPricingDatabaseManager;
    }

    public static VipPayOrderDatabaseManager getVipPayOrderDatabaseManager() {
        return vipPayOrderDatabaseManager;
    }

    public static VideoRenderJobStore getVideoRenderJobStore() {
        return videoRenderJobStore;
    }

    public static VideoRenderQuotaService getVideoRenderQuotaService() {
        return videoRenderQuotaService;
    }

    public static VideoRenderService getVideoRenderService() {
        return videoRenderService;
    }

    public static NotificationService getNotificationService() {
        return notificationService;
    }

    public static ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public static Gson getGson() {
        return gson;
    }

    private static void startDailyRecommendationScheduler() {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(zone);
        long initialDelayMs = Duration.between(now, nextMidnight).toMillis();
        long periodMs = Duration.ofDays(1).toMillis();

        dailyRecommendationScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "daily-reco-scheduler");
            t.setDaemon(true);
            return t;
        });

        dailyRecommendationScheduler.scheduleAtFixedRate(() -> {
            LocalDate today = LocalDate.now(zone);
            try {
                logger.info("开始执行每日推荐任务 date={} timezone=Asia/Shanghai", today);
                dailyRecommendationService.regenerateForAllUsers(today);
                logger.info("每日推荐任务完成 date={}", today);
            } catch (Exception e) {
                logger.error("每日推荐任务执行失败 date={}", today, e);
            }
        }, initialDelayMs, periodMs, TimeUnit.MILLISECONDS);

        logger.info("每日推荐定时任务已启动：Asia/Shanghai 每日00:00执行，首次延迟 {} 秒", initialDelayMs / 1000);
    }
}
