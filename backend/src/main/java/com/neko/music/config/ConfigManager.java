package com.neko.music.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

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

    /** Jetty / Hikari / Redis 连接池，可在 config.yml 的 performance、redis.pool_max_total 中覆盖 */
    private int jettyMaxThreads = 200;
    private int jettyMinThreads = 10;
    private long jettyIdleTimeoutMs = 60_000;
    private int hikariMaximumPoolSize = 20;
    private int hikariMinimumIdle = 5;
    private int redisPoolMaxTotal = 32;

    /** ZPay（易支付兼容）：见 https://z-pay.cn/doc.html */
    private boolean zpayEnabled = false;
    private String zpayPid = "";
    private String zpayKey = "";
    private String zpayMapiUrl = "https://zpayz.cn/mapi.php";
    private String zpaySubmitUrl = "https://zpayz.cn/submit.php";
    /** 异步通知：可为站点根或已是完整 notify URL，见 {@link #getZpayNotifyUrl()} */
    private String zpayPublicBaseUrl = "";
    private String zpayFrontendReturnUrl = "";

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
                
                // 读取邮箱白名单配置
                if (configNode.has("whitelist_email")) {
                    JsonNode whitelistNode = configNode.get("whitelist_email");
                    if (whitelistNode.isArray()) {
                        StringBuilder whitelistBuilder = new StringBuilder();
                        for (JsonNode node : whitelistNode) {
                            if (whitelistBuilder.length() > 0) {
                                whitelistBuilder.append(",");
                            }
                            whitelistBuilder.append(node.asText().trim());
                        }
                        emailWhitelist = whitelistBuilder.toString();
                    }
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

                JsonNode zpayNode = configNode.get("zpay");
                if (zpayNode != null) {
                    if (zpayNode.has("enabled")) zpayEnabled = zpayNode.get("enabled").asBoolean();
                    if (zpayNode.has("pid")) zpayPid = zpayNode.get("pid").asText("").trim();
                    if (zpayNode.has("key")) zpayKey = zpayNode.get("key").asText("").trim();
                    if (zpayNode.has("mapi_url")) zpayMapiUrl = zpayNode.get("mapi_url").asText(zpayMapiUrl).trim();
                    if (zpayNode.has("submit_url")) zpaySubmitUrl = zpayNode.get("submit_url").asText(zpaySubmitUrl).trim();
                    if (zpayNode.has("public_base_url")) zpayPublicBaseUrl = zpayNode.get("public_base_url").asText("").trim();
                    if (zpayNode.has("frontend_return_url")) zpayFrontendReturnUrl = zpayNode.get("frontend_return_url").asText("").trim();
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
            logger.info("  ZPay 支付: enabled={}, pidConfigured={}, publicBaseUrlConfigured={}",
                    zpayEnabled, !zpayPid.isEmpty(), !zpayPublicBaseUrl.isEmpty());
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

    public String getZpaySubmitUrl() {
        return zpaySubmitUrl;
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