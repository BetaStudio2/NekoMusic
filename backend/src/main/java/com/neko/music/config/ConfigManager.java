package com.neko.music.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Locale;

public class ConfigManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);
    
    private String mysqlHost = "localhost";
    private int mysqlPort = 3306;
    private String mysqlDatabase = "nek_music";
    private String mysqlUsername = "root";
    private String mysqlPassword = "";
    private int port = 8080; // 默认端口
    
    // SMTP邮件服务器配置
    private String smtpHost = "smtp.gmail.com";
    private int smtpPort = 587;
    private String smtpUsername = "";
    private String smtpPassword = "";
    private boolean smtpSsl = false;
    private boolean smtpTls = true;
    private String emailWhitelist = ""; // 默认为空，表示不限制
    
    // Redis配置
    private String redisHost = "localhost";
    private int redisPort = 6379;
    private String redisPassword = "";
    
    // JWT配置
    private String jwtSecret = "defaultSecretKeyForNekoMusic";
    private int jwtExpiration = 86400; // 24小时（秒）
    
    // Msg配置
    private String msgUrl = "";
    private String msgToken = "";

    // IP频率限制配置
    private boolean rateLimitEnabled = true;
    private int rateLimitTimeWindow = 60; // 时间窗口（秒）
    private int rateLimitMaxRequests = 10; // 最大请求数
    private int rateLimitBlockDuration = 3600; // 封锁时间（秒）
    private boolean rateLimitSilentTimeout = true; // 是否静默超时

    /** 验证码发信：同邮箱冷却秒数 */
    private int verificationCodeEmailCooldownSeconds = 60;

    /** Jetty / Hikari / Redis 连接池，可在 config.yml 的 performance、redis.pool_max_total 中覆盖 */
    private int jettyMaxThreads = 200;
    private int jettyMinThreads = 10;
    private long jettyIdleTimeoutMs = 60_000;
    private int hikariMaximumPoolSize = 20;
    private int hikariMinimumIdle = 5;
    private int redisPoolMaxTotal = 32;

    /** 横屏短视频渲染：见 config.yml video_render */
    private boolean videoRenderEnabled = true;
    /** auto / ffmpeg / 绝对路径，见 {@link #getVideoRenderFfmpegPath()} */
    private String videoRenderFfmpegPath = "auto";
    /** true 时优先 JAR 内嵌 linux-x86_64 FFmpeg（BtbN 构建，含 NVENC/CUDA） */
    private boolean videoRenderPreferBundledFfmpeg = true;
    /**
     * 成片视频编码：libx264（CPU）或 h264_nvenc（NVIDIA GPU，需驱动与带 NVENC 的 FFmpeg）。
     * 配置可写别名：cpu / x264 → libx264；nvenc → h264_nvenc。
     */
    private String videoRenderVideoCodec = "h264_nvenc";
    /**
     * 渲染管线：{@code cpu_legacy} 与 {@code cuda_native} 共用同一套 CPU 滤镜图（圆形封面、封面外径向频谱条等）；
     * {@code cuda_native} 仅将成片编码固定为 {@code h264_nvenc}，{@code cpu_legacy} 可由 video_codec 选 libx264 或 nvenc。
     */
    private String videoRenderPipeline = "cuda_native";
    /** 非 VIP 单次最长秒数 */
    private int videoRenderNonVipMaxDurationSec = 15;
    /** 非 VIP 每日次数上限（Redis，东八区自然日） */
    private int videoRenderNonVipDailyLimit = 10;
    /** 异步渲染线程池大小 */
    private int videoRenderWorkerThreads = 2;
    /** 渲染完成邮件中的前端站点根 URL */
    private String videoRenderNotifyFrontendBaseUrl = "";
    /** 成片与 ASS 在 /tmp/.neko 中的保留小时数，到期自动删除 */
    private int videoRenderArtifactRetentionHours = 3;

    /** 单曲搜索无本地结果时，通过 NeteaseCloudMusicApi 补全入库 */
    private boolean neteaseSearchFillEnabled = false;
    private String neteaseApiBaseUrl = "http://127.0.0.1:3000";
    private String neteaseCookie = "";
    private String neteaseQuality = "hires";
    /** 空或 auto 表示按歌曲元数据自动推断 */
    private String neteaseFillLanguage = "";
    private Integer neteaseFillUploadUserId = null;
    private int neteaseHttpTimeoutSeconds = 45;

    /** 运行目录所在分区最低可用空间（GB，十进制 1 GB = 10⁹ 字节），不足时禁止音乐上传与网易云补全 */
    private int storageMinFreeGb = 10;

    private static final long STORAGE_BYTES_PER_GB = 1_000_000_000L;

    /** ZPay（易支付兼容）：见 https://z-pay.cn/doc.html */
    private boolean zpayEnabled = false;
    private String zpayPid = "";
    private String zpayKey = "";
    private String zpayMapiUrl = "https://zpayz.cn/mapi.php";
    /** 异步通知：可为站点根或已是完整 notify URL，见 {@link #getZpayNotifyUrl()} */
    private String zpayPublicBaseUrl = "";
    private String zpayFrontendReturnUrl = "";

    /** 每日推荐 AI（OpenAI 兼容） */
    private boolean recommendationAiEnabled = false;
    private String recommendationAiBaseUrl = "https://api.openai.com/v1";
    private String recommendationAiApiKey = "";
    private String recommendationAiModel = "gpt-4o-mini";
    private double recommendationAiTemperature = 0.3d;
    private double recommendationAiTopP = 0.9d;
    private int recommendationAiMaxTokens = 800;
    private int recommendationAiTimeoutSeconds = 20;
    private int recommendationAiDailyLimit = 30;
    private boolean recommendationAiFallbackToRule = true;

    private ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

    public void loadConfig() {
        try {
            // 首先检查外部配置文件是否存在，如果不存在则从classpath复制一份
            String configPath = System.getProperty("user.dir") + File.separator + "config.yml";
            File externalConfigFile = new File(configPath);
            
            if (!externalConfigFile.exists()) {
                // 外部配置文件不存在，从classpath复制默认配置
                copyDefaultConfig(externalConfigFile);
            }
            
            JsonNode configNode = null;
            
            // 从外部文件读取配置（优先级更高）
            if (externalConfigFile.exists()) {
                try (InputStream inputStream = new FileInputStream(externalConfigFile)) {
                    configNode = objectMapper.readTree(inputStream);
                    logger.info("从外部文件加载配置: {}", externalConfigFile.getAbsolutePath());
                }
            }
            
            // 如果找到了配置节点，读取配置值
            if (configNode != null) {
                // 读取MySQL配置
                JsonNode mysqlNode = configNode.get("mysql");
                if (mysqlNode != null) {
                    if (mysqlNode.has("host")) mysqlHost = mysqlNode.get("host").asText();
                    if (mysqlNode.has("port")) mysqlPort = mysqlNode.get("port").asInt();
                    if (mysqlNode.has("database")) mysqlDatabase = mysqlNode.get("database").asText();
                    if (mysqlNode.has("username")) mysqlUsername = mysqlNode.get("username").asText();
                    if (mysqlNode.has("password")) mysqlPassword = mysqlNode.get("password").asText();
                }
                
                // 读取端口配置
                if (configNode.has("port")) {
                    port = configNode.get("port").asInt();
                }

                JsonNode performanceNode = configNode.get("performance");
                if (performanceNode != null) {
                    if (performanceNode.has("jetty_max_threads")) {
                        jettyMaxThreads = performanceNode.get("jetty_max_threads").asInt();
                    }
                    if (performanceNode.has("jetty_min_threads")) {
                        jettyMinThreads = performanceNode.get("jetty_min_threads").asInt();
                    }
                    if (performanceNode.has("jetty_idle_timeout_ms")) {
                        jettyIdleTimeoutMs = performanceNode.get("jetty_idle_timeout_ms").asLong();
                    }
                    if (performanceNode.has("hikari_maximum_pool_size")) {
                        hikariMaximumPoolSize = performanceNode.get("hikari_maximum_pool_size").asInt();
                    }
                    if (performanceNode.has("hikari_minimum_idle")) {
                        hikariMinimumIdle = performanceNode.get("hikari_minimum_idle").asInt();
                    }
                }
                
                // 读取SMTP服务器配置
                JsonNode smtpNode = configNode.get("smtp");
                if (smtpNode != null) {
                    if (smtpNode.has("host")) smtpHost = smtpNode.get("host").asText();
                    if (smtpNode.has("port")) smtpPort = smtpNode.get("port").asInt();
                    if (smtpNode.has("username")) smtpUsername = smtpNode.get("username").asText();
                    if (smtpNode.has("password")) smtpPassword = smtpNode.get("password").asText();
                    if (smtpNode.has("ssl")) smtpSsl = smtpNode.get("ssl").asBoolean();
                    if (smtpNode.has("tls")) smtpTls = smtpNode.get("tls").asBoolean();
                }
                
                // 读取邮箱白名单（与发验证码、注册共用；缺省或从配置中删除键 = 不限制）
                emailWhitelist = "";
                if (configNode.has("whitelist_email")) {
                    JsonNode whitelistNode = configNode.get("whitelist_email");
                    if (whitelistNode.isArray()) {
                        StringBuilder whitelistBuilder = new StringBuilder();
                        for (JsonNode node : whitelistNode) {
                            if (node == null || !node.isTextual()) {
                                continue;
                            }
                            String part = node.asText().trim();
                            if (part.isEmpty()) {
                                continue;
                            }
                            if (whitelistBuilder.length() > 0) {
                                whitelistBuilder.append(",");
                            }
                            whitelistBuilder.append(part);
                        }
                        emailWhitelist = whitelistBuilder.toString();
                    } else if (whitelistNode.isTextual()) {
                        emailWhitelist = whitelistNode.asText().trim();
                    }
                }
                if (emailWhitelist.isEmpty()) {
                    logger.info("  邮箱白名单: (未配置，不限制域名)");
                } else {
                    logger.info("  邮箱白名单: {}", emailWhitelist);
                }
                
                // 读取Redis配置
                JsonNode redisNode = configNode.get("redis");
                if (redisNode != null) {
                    if (redisNode.has("host")) redisHost = redisNode.get("host").asText();
                    if (redisNode.has("port")) redisPort = redisNode.get("port").asInt();
                    if (redisNode.has("password")) redisPassword = redisNode.get("password").asText();
                    if (redisNode.has("pool_max_total")) {
                        redisPoolMaxTotal = redisNode.get("pool_max_total").asInt();
                    }
                }
                
                // 读取JWT配置
                JsonNode jwtNode = configNode.get("jwt");
                if (jwtNode != null) {
                    if (jwtNode.has("secret")) jwtSecret = jwtNode.get("secret").asText();
                    if (jwtNode.has("expiration")) jwtExpiration = jwtNode.get("expiration").asInt();
                }
                
                // 读取Msg配置
                JsonNode msgNode = configNode.get("Msg");
                if (msgNode != null) {
                    if (msgNode.has("url")) msgUrl = msgNode.get("url").asText();
                    if (msgNode.has("token")) msgToken = msgNode.get("token").asText();
                }

                // 读取IP频率限制配置
                JsonNode rateLimitNode = configNode.get("rate_limit");
                if (rateLimitNode != null) {
                    if (rateLimitNode.has("enabled")) rateLimitEnabled = rateLimitNode.get("enabled").asBoolean();
                    if (rateLimitNode.has("time_window")) rateLimitTimeWindow = rateLimitNode.get("time_window").asInt();
                    if (rateLimitNode.has("max_requests")) rateLimitMaxRequests = rateLimitNode.get("max_requests").asInt();
                    if (rateLimitNode.has("block_duration")) rateLimitBlockDuration = rateLimitNode.get("block_duration").asInt();
                    if (rateLimitNode.has("silent_timeout")) rateLimitSilentTimeout = rateLimitNode.get("silent_timeout").asBoolean();
                }

                JsonNode verificationCodeNode = configNode.get("verification_code");
                if (verificationCodeNode != null && verificationCodeNode.has("email_cooldown_seconds")) {
                    verificationCodeEmailCooldownSeconds = verificationCodeNode.get("email_cooldown_seconds").asInt();
                }

                JsonNode videoRenderNode = configNode.get("video_render");
                if (videoRenderNode != null) {
                    if (videoRenderNode.has("enabled")) {
                        videoRenderEnabled = videoRenderNode.get("enabled").asBoolean();
                    }
                    if (videoRenderNode.has("ffmpeg_path")) {
                        videoRenderFfmpegPath = videoRenderNode.get("ffmpeg_path").asText(videoRenderFfmpegPath).trim();
                    }
                    if (videoRenderNode.has("prefer_bundled_ffmpeg")) {
                        videoRenderPreferBundledFfmpeg = videoRenderNode.get("prefer_bundled_ffmpeg").asBoolean();
                    }
                    if (videoRenderNode.has("video_codec")) {
                        videoRenderVideoCodec = normalizeVideoRenderCodec(
                                videoRenderNode.get("video_codec").asText(videoRenderVideoCodec));
                    }
                    if (videoRenderNode.has("pipeline")) {
                        videoRenderPipeline = normalizeVideoRenderPipeline(
                                videoRenderNode.get("pipeline").asText(videoRenderPipeline));
                    }
                    if (videoRenderNode.has("non_vip_max_duration_sec")) {
                        videoRenderNonVipMaxDurationSec = videoRenderNode.get("non_vip_max_duration_sec").asInt();
                    }
                    if (videoRenderNode.has("non_vip_daily_limit")) {
                        videoRenderNonVipDailyLimit = videoRenderNode.get("non_vip_daily_limit").asInt();
                    }
                    if (videoRenderNode.has("worker_threads")) {
                        videoRenderWorkerThreads = videoRenderNode.get("worker_threads").asInt();
                    }
                    if (videoRenderNode.has("notify_frontend_base_url")) {
                        videoRenderNotifyFrontendBaseUrl = videoRenderNode.get("notify_frontend_base_url").asText("").trim();
                    }
                    if (videoRenderNode.has("artifact_retention_hours")) {
                        videoRenderArtifactRetentionHours = videoRenderNode.get("artifact_retention_hours").asInt();
                    }
                }

                JsonNode storageNode = configNode.get("storage");
                if (storageNode != null && storageNode.has("min_free_gb")) {
                    storageMinFreeGb = storageNode.get("min_free_gb").asInt();
                }

                JsonNode neteaseFillNode = configNode.get("netease_search_fill");
                if (neteaseFillNode != null) {
                    if (neteaseFillNode.has("enabled")) {
                        neteaseSearchFillEnabled = neteaseFillNode.get("enabled").asBoolean();
                    }
                    if (neteaseFillNode.has("api_base_url")) {
                        neteaseApiBaseUrl = neteaseFillNode.get("api_base_url").asText(neteaseApiBaseUrl).trim();
                    }
                    if (neteaseFillNode.has("cookie")) {
                        neteaseCookie = neteaseFillNode.get("cookie").asText("").trim();
                    }
                    if (neteaseFillNode.has("quality")) {
                        neteaseQuality = neteaseFillNode.get("quality").asText(neteaseQuality).trim();
                    }
                    if (neteaseFillNode.has("language")) {
                        neteaseFillLanguage = neteaseFillNode.get("language").asText(neteaseFillLanguage).trim();
                    }
                    if (neteaseFillNode.has("upload_user_id") && !neteaseFillNode.get("upload_user_id").isNull()) {
                        neteaseFillUploadUserId = neteaseFillNode.get("upload_user_id").asInt();
                    }
                    if (neteaseFillNode.has("http_timeout_seconds")) {
                        neteaseHttpTimeoutSeconds = neteaseFillNode.get("http_timeout_seconds").asInt();
                    }
                }

                JsonNode zpayNode = configNode.get("zpay");
                if (zpayNode != null) {
                    if (zpayNode.has("enabled")) zpayEnabled = zpayNode.get("enabled").asBoolean();
                    if (zpayNode.has("pid")) zpayPid = zpayNode.get("pid").asText("").trim();
                    if (zpayNode.has("key")) zpayKey = zpayNode.get("key").asText("").trim();
                    if (zpayNode.has("mapi_url")) zpayMapiUrl = zpayNode.get("mapi_url").asText(zpayMapiUrl).trim();
                    if (zpayNode.has("public_base_url")) zpayPublicBaseUrl = zpayNode.get("public_base_url").asText("").trim();
                    if (zpayNode.has("frontend_return_url")) zpayFrontendReturnUrl = zpayNode.get("frontend_return_url").asText("").trim();
                }

                JsonNode recommendationAiNode = configNode.get("recommendation_ai");
                if (recommendationAiNode != null) {
                    if (recommendationAiNode.has("enabled")) {
                        recommendationAiEnabled = recommendationAiNode.get("enabled").asBoolean();
                    }
                    if (recommendationAiNode.has("base_url")) {
                        recommendationAiBaseUrl = recommendationAiNode.get("base_url")
                                .asText(recommendationAiBaseUrl).trim();
                    }
                    if (recommendationAiNode.has("api_key")) {
                        recommendationAiApiKey = recommendationAiNode.get("api_key").asText("").trim();
                    }
                    if (recommendationAiNode.has("model")) {
                        recommendationAiModel = recommendationAiNode.get("model")
                                .asText(recommendationAiModel).trim();
                    }
                    if (recommendationAiNode.has("temperature")) {
                        recommendationAiTemperature = recommendationAiNode.get("temperature").asDouble();
                    }
                    if (recommendationAiNode.has("top_p")) {
                        recommendationAiTopP = recommendationAiNode.get("top_p").asDouble();
                    }
                    if (recommendationAiNode.has("max_tokens")) {
                        recommendationAiMaxTokens = recommendationAiNode.get("max_tokens").asInt();
                    }
                    if (recommendationAiNode.has("timeout_seconds")) {
                        recommendationAiTimeoutSeconds = recommendationAiNode.get("timeout_seconds").asInt();
                    }
                    if (recommendationAiNode.has("daily_limit")) {
                        recommendationAiDailyLimit = recommendationAiNode.get("daily_limit").asInt();
                    }
                    if (recommendationAiNode.has("fallback_to_rule")) {
                        recommendationAiFallbackToRule = recommendationAiNode.get("fallback_to_rule").asBoolean();
                    }
                }

            }

            clampPerformanceConfig();
            
            logger.info("配置加载完成:");
            logger.info("  MySQL Host: {}", mysqlHost);
            logger.info("  MySQL Port: {}", mysqlPort);
            logger.info("  MySQL Database: {}", mysqlDatabase);
            logger.info("  MySQL Username: {}", mysqlUsername);
            logger.info("  Port: {}", port);
            logger.info("  SMTP Host: {}", smtpHost);
            logger.info("  SMTP Port: {}", smtpPort);
            logger.info("  SMTP Username: {}", smtpUsername);
            logger.info("  SMTP SSL: {}", smtpSsl);
            logger.info("  SMTP TLS: {}", smtpTls);
            logger.info("  Jetty 线程池: min={}, max={}, idleTimeoutMs={}", jettyMinThreads, jettyMaxThreads, jettyIdleTimeoutMs);
            logger.info("  HikariCP: maximumPoolSize={}, minimumIdle={}", hikariMaximumPoolSize, hikariMinimumIdle);
            logger.info("  Redis 连接池 maxTotal: {}", redisPoolMaxTotal);
            logger.info("  视频渲染: enabled={}, pipeline={}, ffmpegPath={}, preferBundled={}, videoCodec={}, nonVipMaxSec={}, nonVipDailyLimit={}",
                    videoRenderEnabled, videoRenderPipeline, videoRenderFfmpegPath, videoRenderPreferBundledFfmpeg,
                    videoRenderVideoCodec, videoRenderNonVipMaxDurationSec, videoRenderNonVipDailyLimit);
            logger.info("  存储保护: minFreeGb={}（不足时禁止音乐上传与网易云补全）", storageMinFreeGb);
            logger.info("  网易云搜索补全: enabled={}, apiBaseUrl={}, quality={}, cookieConfigured={}",
                    neteaseSearchFillEnabled, neteaseApiBaseUrl, neteaseQuality, !neteaseCookie.isEmpty());
            logger.info("  ZPay 支付: enabled={}, pidConfigured={}, publicBaseUrlConfigured={}",
                    zpayEnabled, !zpayPid.isEmpty(), !zpayPublicBaseUrl.isEmpty());
            logger.info("  推荐 AI(OpenAI): enabled={}, baseUrl={}, model={}, apiKeyConfigured={}, dailyLimit={}, fallbackToRule={}",
                    recommendationAiEnabled, recommendationAiBaseUrl, recommendationAiModel,
                    !recommendationAiApiKey.isEmpty(), recommendationAiDailyLimit, recommendationAiFallbackToRule);
        } catch (Exception e) {
            logger.error("加载配置时出错", e);
            clampPerformanceConfig();
        }
    }

    private void clampPerformanceConfig() {
        jettyMaxThreads = Math.max(16, Math.min(2000, jettyMaxThreads));
        jettyMinThreads = Math.max(1, Math.min(jettyMinThreads, jettyMaxThreads - 1));
        jettyIdleTimeoutMs = Math.max(1000L, Math.min(600_000L, jettyIdleTimeoutMs));
        hikariMaximumPoolSize = Math.max(2, Math.min(256, hikariMaximumPoolSize));
        hikariMinimumIdle = Math.max(0, Math.min(hikariMinimumIdle, hikariMaximumPoolSize));
        redisPoolMaxTotal = Math.max(4, Math.min(256, redisPoolMaxTotal));
        videoRenderNonVipMaxDurationSec = Math.max(5, Math.min(120, videoRenderNonVipMaxDurationSec));
        videoRenderNonVipDailyLimit = Math.max(1, Math.min(1000, videoRenderNonVipDailyLimit));
        videoRenderWorkerThreads = Math.max(1, Math.min(16, videoRenderWorkerThreads));
        videoRenderArtifactRetentionHours = Math.max(1, Math.min(168, videoRenderArtifactRetentionHours));
        storageMinFreeGb = Math.max(1, Math.min(1024, storageMinFreeGb));
        neteaseHttpTimeoutSeconds = Math.max(5, Math.min(300, neteaseHttpTimeoutSeconds));
        if (neteaseApiBaseUrl == null || neteaseApiBaseUrl.isBlank()) {
            neteaseApiBaseUrl = "http://127.0.0.1:3000";
        }
        if (neteaseQuality == null || neteaseQuality.isBlank()) {
            neteaseQuality = "hires";
        }
        if (neteaseFillLanguage == null) {
            neteaseFillLanguage = "";
        }
        if (recommendationAiBaseUrl == null || recommendationAiBaseUrl.isBlank()) {
            recommendationAiBaseUrl = "https://api.openai.com/v1";
        }
        if (recommendationAiModel == null || recommendationAiModel.isBlank()) {
            recommendationAiModel = "gpt-4o-mini";
        }
        recommendationAiTemperature = Math.max(0.0d, Math.min(2.0d, recommendationAiTemperature));
        recommendationAiTopP = Math.max(0.0d, Math.min(1.0d, recommendationAiTopP));
        recommendationAiMaxTokens = Math.max(64, Math.min(16_384, recommendationAiMaxTokens));
        recommendationAiTimeoutSeconds = Math.max(3, Math.min(300, recommendationAiTimeoutSeconds));
        recommendationAiDailyLimit = Math.max(1, Math.min(500, recommendationAiDailyLimit));
        if (videoRenderFfmpegPath == null || videoRenderFfmpegPath.isBlank()) {
            videoRenderFfmpegPath = "auto";
        }
        videoRenderVideoCodec = normalizeVideoRenderCodec(videoRenderVideoCodec);
        videoRenderPipeline = normalizeVideoRenderPipeline(videoRenderPipeline);
    }

    private static String normalizeVideoRenderPipeline(String raw) {
        if (raw == null || raw.isBlank()) {
            return "cuda_native";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "cpu", "cpu_legacy", "legacy" -> "cpu_legacy";
            case "cuda", "cuda_native", "gpu", "full_gpu" -> "cuda_native";
            default -> {
                LoggerFactory.getLogger(ConfigManager.class).warn(
                        "未知的 video_render.pipeline: {}，回退为 cpu_legacy", raw);
                yield "cpu_legacy";
            }
        };
    }

    private static String normalizeVideoRenderCodec(String raw) {
        if (raw == null || raw.isBlank()) {
            return "h264_nvenc";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "cpu", "x264", "libx264" -> "libx264";
            case "nvenc", "gpu", "h264_nvenc" -> "h264_nvenc";
            default -> {
                LoggerFactory.getLogger(ConfigManager.class).warn(
                        "未知的 video_render.video_codec: {}，回退为 libx264", raw);
                yield "libx264";
            }
        };
    }
    
    /**
     * 从classpath复制默认配置文件到外部位置
     */
    private void copyDefaultConfig(File targetFile) {
        try {
            // 确保目标目录存在
            File parentDir = targetFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            // 从classpath获取默认配置
            try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("config.yml")) {
                if (inputStream != null) {
                    // 将配置文件写入外部位置
                    try (FileOutputStream outputStream = new FileOutputStream(targetFile)) {
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                        logger.info("已将默认配置文件复制到: {}", targetFile.getAbsolutePath());
                    }
                } else {
                    logger.warn("无法找到classpath中的默认配置文件");
                }
            }
        } catch (IOException e) {
            logger.error("复制默认配置文件时出错", e);
        }
    }

    // Getters
    public String getMysqlHost() {
        return mysqlHost;
    }

    public int getMysqlPort() {
        return mysqlPort;
    }

    public String getMysqlDatabase() {
        return mysqlDatabase;
    }

    public String getMysqlUsername() {
        return mysqlUsername;
    }

    public String getMysqlPassword() {
        return mysqlPassword;
    }
    
    public int getPort() {
        return port;
    }
    
    public String getSmtpHost() {
        return smtpHost;
    }
    
    public int getSmtpPort() {
        return smtpPort;
    }
    
    public String getSmtpUsername() {
        return smtpUsername;
    }
    
    public String getSmtpPassword() {
        return smtpPassword;
    }
    
    public boolean isSmtpSsl() {
        return smtpSsl;
    }
    
    public boolean isSmtpTls() {
        return smtpTls;
    }
    
    public String getEmailWhitelist() {
        return emailWhitelist;
    }
    
    public String getRedisHost() {
        return redisHost;
    }
    
    public int getRedisPort() {
        return redisPort;
    }

    public String getRedisPassword() {
        return redisPassword;
    }
    
    public String getJwtSecret() {
        return jwtSecret;
    }
    
    public int getJwtExpiration() {
        return jwtExpiration;
    }
    
    public String getMsgUrl() {
        return msgUrl;
    }
    
    public String getMsgToken() {
        return msgToken;
    }

    public boolean isRateLimitEnabled() {
        return rateLimitEnabled;
    }

    public int getRateLimitTimeWindow() {
        return rateLimitTimeWindow;
    }

    public int getRateLimitMaxRequests() {
        return rateLimitMaxRequests;
    }

    public int getRateLimitBlockDuration() {
        return rateLimitBlockDuration;
    }

    public boolean isRateLimitSilentTimeout() {
        return rateLimitSilentTimeout;
    }

    public int getVerificationCodeEmailCooldownSeconds() {
        return verificationCodeEmailCooldownSeconds;
    }

    public int getJettyMaxThreads() {
        return jettyMaxThreads;
    }

    public int getJettyMinThreads() {
        return jettyMinThreads;
    }

    public long getJettyIdleTimeoutMs() {
        return jettyIdleTimeoutMs;
    }

    public int getHikariMaximumPoolSize() {
        return hikariMaximumPoolSize;
    }

    public int getHikariMinimumIdle() {
        return hikariMinimumIdle;
    }

    public int getRedisPoolMaxTotal() {
        return redisPoolMaxTotal;
    }

    public boolean isVideoRenderEnabled() {
        return videoRenderEnabled;
    }

    public String getVideoRenderFfmpegPath() {
        return videoRenderFfmpegPath;
    }

    public boolean isVideoRenderPreferBundledFfmpeg() {
        return videoRenderPreferBundledFfmpeg;
    }

    /** 成片编码：{@code libx264} 或 {@code h264_nvenc} */
    public String getVideoRenderVideoCodec() {
        return videoRenderVideoCodec;
    }

    /** {@code cpu_legacy} 或 {@code cuda_native} */
    public String getVideoRenderPipeline() {
        return videoRenderPipeline;
    }

    public int getVideoRenderNonVipMaxDurationSec() {
        return videoRenderNonVipMaxDurationSec;
    }

    public int getVideoRenderNonVipDailyLimit() {
        return videoRenderNonVipDailyLimit;
    }

    public int getVideoRenderWorkerThreads() {
        return videoRenderWorkerThreads;
    }

    public int getVideoRenderArtifactRetentionHours() {
        return videoRenderArtifactRetentionHours;
    }

    /** 渲染完成邮件中的前端站点根（无尾斜杠），用于拼接 /detail/{musicId}?videoJob=… */
    public String getVideoRenderNotifyFrontendBaseUrl() {
        if (videoRenderNotifyFrontendBaseUrl != null && !videoRenderNotifyFrontendBaseUrl.isEmpty()) {
            return trimTrailingSlash(videoRenderNotifyFrontendBaseUrl);
        }
        String vip = getZpayFrontendReturnUrl();
        if (!vip.isEmpty() && vip.endsWith("/vip")) {
            return trimTrailingSlash(vip.substring(0, vip.length() - 4));
        }
        if (!vip.isEmpty()) {
            return vip;
        }
        return zpaySiteRootFromPublicBase(getZpayPublicBaseUrl());
    }

    public int getStorageMinFreeGb() {
        return storageMinFreeGb;
    }

    public long getStorageMinFreeBytes() {
        return storageMinFreeGb * STORAGE_BYTES_PER_GB;
    }

    public long getStorageBytesPerGb() {
        return STORAGE_BYTES_PER_GB;
    }

    public boolean isNeteaseSearchFillEnabled() {
        return neteaseSearchFillEnabled;
    }

    public String getNeteaseApiBaseUrl() {
        return trimTrailingSlash(neteaseApiBaseUrl);
    }

    public String getNeteaseCookie() {
        return neteaseCookie == null ? "" : neteaseCookie;
    }

    public String getNeteaseQuality() {
        return neteaseQuality;
    }

    public String getNeteaseFillLanguage() {
        return neteaseFillLanguage;
    }

    public Integer getNeteaseFillUploadUserId() {
        return neteaseFillUploadUserId;
    }

    public int getNeteaseHttpTimeoutSeconds() {
        return neteaseHttpTimeoutSeconds;
    }

    public boolean isZpayEnabled() {
        return zpayEnabled;
    }

    public String getZpayPid() {
        return zpayPid;
    }

    public String getZpayKey() {
        return zpayKey;
    }

    public String getZpayMapiUrl() {
        return zpayMapiUrl;
    }

    public String getZpayPublicBaseUrl() {
        return trimTrailingSlash(zpayPublicBaseUrl);
    }

    /**
     * ZPay 异步通知完整 URL：若 {@code public_base_url} 已包含 {@code /api/payment/zpay/notify} 则原样使用，
     * 否则视为站点根并在其后拼接 {@code /api/payment/zpay/notify}。
     */
    public String getZpayNotifyUrl() {
        String pub = zpayPublicBaseUrl == null ? "" : zpayPublicBaseUrl.trim();
        if (pub.isEmpty()) {
            return "";
        }
        pub = trimTrailingSlash(pub);
        if (pub.contains("/api/payment/zpay/notify")) {
            return pub;
        }
        return pub + "/api/payment/zpay/notify";
    }

    public String getZpayFrontendReturnUrl() {
        String raw = zpayFrontendReturnUrl.trim();
        if (!raw.isEmpty()) {
            return trimTrailingSlash(raw);
        }
        String site = zpaySiteRootFromPublicBase(getZpayPublicBaseUrl());
        if (site.isEmpty()) {
            return "";
        }
        return site + "/vip";
    }

    public boolean isRecommendationAiEnabled() {
        return recommendationAiEnabled;
    }

    public String getRecommendationAiBaseUrl() {
        return trimTrailingSlash(recommendationAiBaseUrl);
    }

    public String getRecommendationAiApiKey() {
        return recommendationAiApiKey == null ? "" : recommendationAiApiKey;
    }

    public String getRecommendationAiModel() {
        return recommendationAiModel;
    }

    public double getRecommendationAiTemperature() {
        return recommendationAiTemperature;
    }

    public double getRecommendationAiTopP() {
        return recommendationAiTopP;
    }

    public int getRecommendationAiMaxTokens() {
        return recommendationAiMaxTokens;
    }

    public int getRecommendationAiTimeoutSeconds() {
        return recommendationAiTimeoutSeconds;
    }

    public int getRecommendationAiDailyLimit() {
        return recommendationAiDailyLimit;
    }

    public boolean isRecommendationAiFallbackToRule() {
        return recommendationAiFallbackToRule;
    }

    /** 从 public_base_url 推出站点根（用于 return_url 默认 /vip） */
    private static String zpaySiteRootFromPublicBase(String publicTrimmed) {
        if (publicTrimmed == null || publicTrimmed.isEmpty()) {
            return "";
        }
        String marker = "/api/payment/zpay/notify";
        int i = publicTrimmed.indexOf(marker);
        if (i > 0) {
            return trimTrailingSlash(publicTrimmed.substring(0, i));
        }
        return publicTrimmed;
    }

    private static String trimTrailingSlash(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        String t = s.trim();
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }
}