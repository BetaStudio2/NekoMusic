package com.neko.music;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.neko.music.config.ConfigManager;
import com.neko.music.database.AdminDatabaseManager;
import com.neko.music.database.DatabaseManager;
import com.neko.music.database.DatabaseInitializer;
import com.neko.music.service.VideoRenderJobStore;
import com.neko.music.database.VipPayOrderDatabaseManager;
import com.neko.music.database.VipPricingDatabaseManager;
import com.neko.music.handlers.*;

import com.neko.music.service.AdminAuthService;
import com.neko.music.service.EmailService;
import com.neko.music.service.NotificationService;
import com.neko.music.service.PlaylistService;
import com.neko.music.service.RedisService;
import com.neko.music.service.RedisTokenStore;
import com.neko.music.service.UserAuthService;
import com.neko.music.service.IPRateLimitService;
import com.neko.music.service.VideoRenderQuotaService;
import com.neko.music.service.VideoRenderService;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import com.neko.music.filter.CorsFilter;
import com.neko.music.filter.IPRateLimitFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.DispatcherType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.EnumSet;

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
        
        // 初始化数据库管理器
        databaseManager = new DatabaseManager(configManager);
        databaseManager.init();
        
        // 初始化数据库表
        DatabaseInitializer.initializeTables(databaseManager);

        vipPricingDatabaseManager = new VipPricingDatabaseManager(databaseManager);
        vipPayOrderDatabaseManager = new VipPayOrderDatabaseManager(databaseManager);

        // 初始化Redis服务（视频配额依赖 Redis，须在 video 服务之前）
        redisService = new RedisService(configManager);
        ipRateLimitService = new IPRateLimitService(configManager, redisService);
        RedisTokenStore tokenStore = new RedisTokenStore(redisService);

        videoRenderJobStore = new VideoRenderJobStore(redisService, objectMapper);
        videoRenderQuotaService = new VideoRenderQuotaService(configManager, redisService);
        videoRenderService = new VideoRenderService(configManager, videoRenderJobStore);
        Runtime.getRuntime().addShutdownHook(new Thread(videoRenderService::shutdown, "video-render-shutdown"));

        // 初始化管理员数据库管理器和认证服务
        adminDatabaseManager = new AdminDatabaseManager(databaseManager);
        adminAuthService = new AdminAuthService(adminDatabaseManager, tokenStore);
        
        // 初始化邮件服务
        emailService = new EmailService(configManager);

        // 初始化用户认证服务
        userAuthService = new UserAuthService(databaseManager, configManager, emailService, redisService, tokenStore);

        // 初始化歌单服务
        playlistService = new PlaylistService(databaseManager);
        
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
        addCorsFilter(context);
        
        // 注册搜索音乐API处理器
        ServletHolder searchHolder = new ServletHolder(new MusicSearchHandler());
        context.addServlet(searchHolder, "/api/music/search");
        
        // 注册音乐管理API处理器 - 使用单一路径处理所有音乐管理请求
        ServletHolder musicManagementHolder = new ServletHolder(new MusicManagementHandler());
        context.addServlet(musicManagementHolder, "/api/music/*");         // 处理所有音乐管理请求
        
        // 注册管理员登录API处理器
        ServletHolder adminLoginHolder = new ServletHolder(new AdminLoginHandler());
        context.addServlet(adminLoginHolder, "/api/admin/login");
        
        // 注册管理员当前信息API处理器
        ServletHolder adminCurrentHolder = new ServletHolder(new AdminCurrentHandler());
        context.addServlet(adminCurrentHolder, "/api/admin/current");
        
        // 注册管理员统计API处理器
        ServletHolder adminStatsHolder = new ServletHolder(new AdminStatsHandler());
        context.addServlet(adminStatsHolder, "/api/admin/stats");
        
        // 注册管理员用户管理API处理器
        ServletHolder adminUserManagementHolder = new ServletHolder(new AdminUserManagementHandler());
        context.addServlet(adminUserManagementHolder, "/api/admin/users/*");
        
        // 注册管理员创建API处理器
        ServletHolder adminCreateHolder = new ServletHolder(new AdminCreateHandler());
        context.addServlet(adminCreateHolder, "/api/admin/create");
        
        // 注册图表数据API处理器
        ServletHolder chartDataHolder = new ServletHolder(new ChartDataHandler());
        context.addServlet(chartDataHolder, "/api/admin/chart-data");
        
        // 注册文件上传API处理器
        ServletHolder fileUploadHolder = new ServletHolder(new FileUploadHandler());
        jakarta.servlet.MultipartConfigElement multipartConfig = new jakarta.servlet.MultipartConfigElement(System.getProperty("java.io.tmpdir"));
        fileUploadHolder.getRegistration().setMultipartConfig(multipartConfig);
        context.addServlet(fileUploadHolder, "/api/music/upload");
        
        // 注册用户上传API处理器
        ServletHolder userUploadHolder = new ServletHolder(new UserUploadHandler());
        jakarta.servlet.MultipartConfigElement userUploadMultipartConfig = new jakarta.servlet.MultipartConfigElement(System.getProperty("java.io.tmpdir"));
        userUploadHolder.getRegistration().setMultipartConfig(userUploadMultipartConfig);
        context.addServlet(userUploadHolder, "/api/user/upload");
        
        // 注册管理员审核API处理器
        ServletHolder adminUploadAuditHolder = new ServletHolder(new AdminUploadAuditHandler());
        context.addServlet(adminUploadAuditHolder, "/api/admin/audit/*");
        
        // 注册用户上传预览API处理器
        ServletHolder userUploadPreviewHolder = new ServletHolder(new UserUploadPreviewHandler());
        context.addServlet(userUploadPreviewHolder, "/api/user/upload/preview");
        
        // 注册获取用户上传审核通过的音乐API处理器
        ServletHolder getUserUploadedMusicHolder = new ServletHolder(new GetUserUploadedMusicHandler());
        context.addServlet(getUserUploadedMusicHolder, "/api/user/uploaded-music");
        
        // 注册音乐封面API处理器
        ServletHolder musicCoverHolder = new ServletHolder(new MusicCoverHandler());
        context.addServlet(musicCoverHolder, "/api/music/cover/*");
        
        // 注册音乐信息API处理器（无需管理员权限）
        ServletHolder musicInfoHolder = new ServletHolder(new MusicInfoHandler());
        context.addServlet(musicInfoHolder, "/api/music/info/*");

        // /detail/{id} 服务端 HTML（SEO：curl 无 JS 可读 meta）
        ServletHolder musicDetailPageHolder = new ServletHolder(new MusicDetailPageHandler());
        context.addServlet(musicDetailPageHolder, "/detail/*");

        ServletHolder sitemapHolder = new ServletHolder(new SitemapHandler());
        context.addServlet(sitemapHolder, "/sitemap.xml");
        
        // 注册音乐文件API处理器（无需管理员权限）
        ServletHolder musicFileHolder = new ServletHolder(new MusicFileHandler());
        context.addServlet(musicFileHolder, "/api/music/file/*");
        
        // 注册歌词API处理器（无需管理员权限）
        ServletHolder musicLyricsHolder = new ServletHolder(new MusicLyricsHandler());
        context.addServlet(musicLyricsHolder, "/api/music/lyrics/*");

        ServletHolder sensitiveWordCheckHolder = new ServletHolder(new SensitiveWordCheckHandler());
        context.addServlet(sensitiveWordCheckHolder, "/api/sensitive-word/check");

        // 注册播放次数排行榜API处理器（无需管理员权限）
        ServletHolder musicRankingHolder = new ServletHolder(new MusicRankingHandler());
        context.addServlet(musicRankingHolder, "/api/music/ranking");

        // 注册最新上传音乐API处理器（无需管理员权限）
        ServletHolder latestMusicHolder = new ServletHolder(new LatestMusicHandler());
        context.addServlet(latestMusicHolder, "/api/music/latest");

        // 注册用户登录API处理器
        ServletHolder userLoginHolder = new ServletHolder(new UserLoginHandler());
        context.addServlet(userLoginHolder, "/api/user/login");
        
        // 注册用户注册API处理器
        ServletHolder userRegisterHolder = new ServletHolder(new UserRegisterHandler());
        context.addServlet(userRegisterHolder, "/api/user/register");
        
        // 注册发送验证码API处理器
        ServletHolder sendVerificationHolder = new ServletHolder(new SendVerificationHandler());
        context.addServlet(sendVerificationHolder, "/api/user/send-verification");
        
        // 注册发送重置密码验证码API处理器
        ServletHolder sendResetPasswordCodeHolder = new ServletHolder(new SendResetPasswordCodeHandler());
        context.addServlet(sendResetPasswordCodeHolder, "/api/user/send-reset-code");
        
        // 注册重置密码API处理器
        ServletHolder resetPasswordHolder = new ServletHolder(new ResetPasswordHandler());
        context.addServlet(resetPasswordHolder, "/api/user/reset-password");
        
        // 注册用户头像上传API处理器（更具体的路径先注册）
        ServletHolder userAvatarUploadHolder = new ServletHolder(new UserAvatarUploadHandler());
        jakarta.servlet.MultipartConfigElement avatarMultipartConfig = new jakarta.servlet.MultipartConfigElement(System.getProperty("java.io.tmpdir"));
        userAvatarUploadHolder.getRegistration().setMultipartConfig(avatarMultipartConfig);
        context.addServlet(userAvatarUploadHolder, "/api/user/avatar/upload");
        
        // 注册用户头像API处理器
        ServletHolder userAvatarHolder = new ServletHolder(new UserAvatarHandler());
        context.addServlet(userAvatarHolder, "/api/user/avatar/*");
        
        // 注册用户修改密码API处理器
        ServletHolder userPasswordChangeHolder = new ServletHolder(new UserPasswordChangeHandler());
        context.addServlet(userPasswordChangeHolder, "/api/user/password/change");
        
        // 注册用户收藏API处理器
        ServletHolder userFavoriteHolder = new ServletHolder(new UserFavoriteHandler());
        context.addServlet(userFavoriteHolder, "/api/user/favorites/*");
        
        // 注册用户收藏歌单API处理器
        ServletHolder userFavoritePlaylistHolder = new ServletHolder(new UserFavoritePlaylistHandler());
        context.addServlet(userFavoritePlaylistHolder, "/api/user/favorite-playlists/*");
        
        // 注册用户管理API处理器（管理员权限）
        ServletHolder userManagementHolder = new ServletHolder(new UserManagementHandler());
        context.addServlet(userManagementHolder, "/api/users/*");

        ServletHolder vipPricingPublicHolder = new ServletHolder(new VipPricingPublicHandler());
        context.addServlet(vipPricingPublicHolder, "/api/vip/pricing");

        ServletHolder vipPricingAdminHolder = new ServletHolder(new VipPricingAdminHandler());
        context.addServlet(vipPricingAdminHolder, "/api/admin/vip/pricing");

        ServletHolder vipPayCreateHolder = new ServletHolder(new VipPayCreateHandler());
        context.addServlet(vipPayCreateHolder, "/api/vip/pay/create");

        ServletHolder zpayNotifyHolder = new ServletHolder(new ZpayNotifyHandler());
        context.addServlet(zpayNotifyHolder, "/api/payment/zpay/notify");

        ServletHolder videoRenderHolder = new ServletHolder(new VideoRenderHandler());
        context.addServlet(videoRenderHolder, "/api/video/render/*");

        // 注册创建歌单API处理器
        ServletHolder createPlaylistHolder = new ServletHolder(new CreatePlaylistHandler());
        context.addServlet(createPlaylistHolder, "/api/user/playlist/create");

        // 注册获取歌单列表API处理器
        ServletHolder getPlaylistsHolder = new ServletHolder(new GetPlaylistsHandler());
        context.addServlet(getPlaylistsHolder, "/api/user/playlists");

        // 注册获取歌单详情API处理器（无需登录）
        ServletHolder getPlaylistDetailHolder = new ServletHolder(new GetPlaylistDetailHandler());
        context.addServlet(getPlaylistDetailHolder, "/api/playlist/*");

        // 注册搜索歌单API处理器（无需登录）
        ServletHolder searchPlaylistsHolder = new ServletHolder(new SearchPlaylistsHandler());
        context.addServlet(searchPlaylistsHolder, "/api/playlists/search");

        // 注册更新歌单API处理器
        ServletHolder updatePlaylistHolder = new ServletHolder(new UpdatePlaylistHandler());
        context.addServlet(updatePlaylistHolder, "/api/user/playlist/update");

        // 注册删除歌单API处理器
        ServletHolder deletePlaylistHolder = new ServletHolder(new DeletePlaylistHandler());
        context.addServlet(deletePlaylistHolder, "/api/user/playlist/delete");

        // 注册获取歌单音乐列表API处理器
        ServletHolder getPlaylistMusicHolder = new ServletHolder(new GetPlaylistMusicHandler());
        context.addServlet(getPlaylistMusicHolder, "/api/user/playlist/music/*");

        // 注册添加音乐到歌单API处理器
        ServletHolder addMusicToPlaylistHolder = new ServletHolder(new AddMusicToPlaylistHandler());
        context.addServlet(addMusicToPlaylistHolder, "/api/user/playlist/music/add");

        // 注册从歌单中移除音乐API处理器
        ServletHolder removeMusicFromPlaylistHolder = new ServletHolder(new RemoveMusicFromPlaylistHandler());
        context.addServlet(removeMusicFromPlaylistHolder, "/api/user/playlist/music/remove");
        
        // 注册搜索歌手API处理器
        ServletHolder searchArtistsHolder = new ServletHolder(new SearchArtistsHandler());
        context.addServlet(searchArtistsHolder, "/api/artists/search");
        
        // 启动服务器
        server.start();
        logger.info("NekoMusic服务器已在端口{}启动", configManager.getPort());
        logger.info("API端点:");
        logger.info("  POST /api/music/search - 搜索音乐");
        logger.info("  GET /api/user/avatar/* - 获取用户头像");
        logger.info("  POST /api/user/avatar/upload - 上传用户头像 (需要用户登录)");
        logger.info("  POST /api/user/password/change - 修改用户密码 (需要用户登录)");
        logger.info("  GET /api/user/favorites - 获取用户收藏列表 (需要用户登录)");
        logger.info("  POST /api/user/favorites - 添加收藏 (需要用户登录)");
        logger.info("  DELETE /api/user/favorites/{id} - 删除收藏 (需要用户登录)");
        logger.info("  GET /api/user/favorite-playlists - 获取收藏歌单列表 (需要用户登录)");
        logger.info("  POST /api/user/favorite-playlists - 收藏歌单 (需要用户登录)");
        logger.info("  DELETE /api/user/favorite-playlists/{id} - 取消收藏歌单 (需要用户登录)");
        logger.info("  GET /api/user/favorite-playlists/{id} - 获取收藏歌单内音乐 (需要用户登录)");
        logger.info("  GET /api/music/list - 获取音乐列表 (需要管理员登录)");
        logger.info("  GET /api/music/{id} - 获取特定音乐 (需要管理员登录)");
        logger.info("  GET /api/music/info/{id} - 获取音乐信息");
        logger.info("  GET /api/music/file/{id} - 获取音乐文件");
        logger.info("  GET /api/music/cover/{id} - 获取音乐封面");
        logger.info("  GET /api/music/lyrics/{id} - 获取歌词");
        logger.info("  POST /api/music/lyrics/{id} - 更新歌词 (需要管理员登录)");
        logger.info("  POST /api/sensitive-word/check - 违禁词检测（无需登录）");
        logger.info("  GET /api/music/ranking - 获取播放次数排行榜");
        logger.info("  GET /api/music/latest - 获取最新上传的音乐");
        logger.info("  POST /api/music/add - 添加音乐 (需要管理员登录)");
        logger.info("  PUT /api/music/edit - 编辑音乐 (需要管理员登录)");
        logger.info("  DELETE /api/music/delete/{id} - 删除音乐 (需要管理员登录)");
        logger.info("  POST /api/user/playlist/create - 创建歌单 (需要用户登录)");
        logger.info("  GET /api/user/playlists - 获取歌单列表 (需要用户登录)");
        logger.info("  GET /api/playlist/{id} - 获取歌单详情 (无需登录)");
        logger.info("  GET /api/playlists/search?query=xxx - 搜索歌单 (无需登录)");
        logger.info("  POST /api/user/playlist/update - 更新歌单 (需要创建者登录)");
        logger.info("  POST /api/user/playlist/delete - 删除歌单 (需要创建者登录)");
        logger.info("  GET /api/user/playlist/music/{playlistId} - 获取歌单音乐列表 (需要登录)");
        logger.info("  POST /api/user/playlist/music/add - 添加音乐到歌单 (需要创建者登录)");
        logger.info("  POST /api/user/playlist/music/remove - 从歌单中移除音乐 (需要创建者登录)");
        logger.info("  POST /api/playlist/{id} - 获取歌单详情 (无需登录)");
        logger.info("  POST /api/playlists/search - 搜索歌单 (无需登录)");
        logger.info("  POST /api/artists/search - 搜索歌手 (无需登录)");
        logger.info("  GET /api/vip/pricing - 获取 VIP 价目表 (无需登录)");
        logger.info("  POST /api/vip/pay/create - 创建 ZPay VIP 订单 (需要用户登录，config zpay.enabled)");
        logger.info("  GET|POST /api/payment/zpay/notify - ZPay 异步通知 (无登录)");
        logger.info("  POST /api/video/render/create - 创建横屏短视频 (需要用户登录，异步渲染)");
        logger.info("  GET /api/video/render/{{jobId}} - 查询渲染任务 (需要用户登录)");
        logger.info("  GET /api/video/render/{{jobId}}/download - 下载成片 (无需登录)");
        logger.info("  PUT /api/admin/vip/pricing - 全量更新 VIP 价目表 (需要管理员)");
        logger.info("  POST /api/user/upload - 用户上传音乐 (需要用户登录)");
        logger.info("  GET /api/admin/audit/pending - 获取待审核列表 (需要管理员登录)");
        logger.info("  POST /api/admin/audit/approve/{id} - 审核通过 (需要管理员登录)");
        logger.info("  POST /api/admin/audit/reject/{id} - 审核拒绝 (需要管理员登录)");
        logger.info("  POST /api/admin/login - 管理员登录");
        logger.info("  GET /api/admin/stats - 管理员统计信息 (需要管理员登录)");
        logger.info("  GET /api/admin/chart-data - 管理员图表数据 (需要管理员登录)");
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

    private static void createAdminIfNotExists(String username, String password) {
        // 检查管理员是否已存在
        if (!adminDatabaseManager.adminExists(username)) {
            boolean created = adminAuthService.createAdmin(username, password, username + "@nekomusic.com");
            if (created) {
                logger.info("管理员账号已创建: {}/{}", username, password);
            } else {
                logger.error("创建管理员账号失败: {}", username);
            }
        } else {
            logger.info("管理员账号已存在: {}", username);
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
    
    private static void addCorsFilter(ServletContextHandler context) {
        context.addFilter(CorsFilter.class, "/*", EnumSet.allOf(DispatcherType.class));
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
}
