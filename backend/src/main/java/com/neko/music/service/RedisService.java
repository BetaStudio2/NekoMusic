package com.neko.music.service;

import com.neko.music.config.ConfigManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedisService {
    private static final Logger logger = LoggerFactory.getLogger(RedisService.class);
    
    private final RedisClient redisClient;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String> syncCommands;
    private final ConfigManager configManager;

    public RedisService(ConfigManager configManager) {
        this.configManager = configManager;

        // 从配置中获取Redis连接信息，如果没有配置则使用默认值
        String redisHost = configManager.getRedisHost();
        int redisPort = configManager.getRedisPort();
        String redisPassword = configManager.getRedisPassword();

        // 构建Redis URL，支持密码认证
        String redisUrl;
        if (redisPassword != null && !redisPassword.isEmpty()) {
            redisUrl = "redis://:" + redisPassword + "@" + redisHost + ":" + redisPort;
        } else {
            redisUrl = "redis://" + redisHost + ":" + redisPort;
        }

        this.redisClient = RedisClient.create(redisUrl);
        this.connection = redisClient.connect();
        this.syncCommands = connection.sync();

        logger.info("已连接到Redis: {}:{}", redisHost, redisPort);
    }
    
    /**
     * 设置键值对并设置过期时间
     */
    public void setWithExpiry(String key, String value, int seconds) {
        try {
            syncCommands.setex(key, seconds, value);
        } catch (Exception e) {
            logger.error("设置Redis键值对失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 获取键对应的值
     */
    public String get(String key) {
        try {
            return syncCommands.get(key);
        } catch (Exception e) {
            logger.error("获取Redis键值失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 删除键
     */
    public void del(String key) {
        try {
            syncCommands.del(key);
        } catch (Exception e) {
            logger.error("删除Redis键失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 检查键是否存在
     */
    public boolean exists(String key) {
        try {
            return syncCommands.exists(key) > 0;
        } catch (Exception e) {
            logger.error("检查Redis键存在性失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 关闭连接
     */
    public void close() {
        try {
            if (connection != null) {
                connection.close();
            }
            if (redisClient != null) {
                redisClient.shutdown();
            }
        } catch (Exception e) {
            logger.error("关闭Redis连接失败: {}", e.getMessage(), e);
        }
    }
}