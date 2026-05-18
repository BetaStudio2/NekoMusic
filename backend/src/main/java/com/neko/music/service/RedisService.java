package com.neko.music.service;

import com.neko.music.config.ConfigManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import io.lettuce.core.support.ConnectionPoolSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

public class RedisService {
    private static final Logger logger = LoggerFactory.getLogger(RedisService.class);

    private final RedisClient redisClient;
    private final GenericObjectPool<StatefulRedisConnection<String, String>> connectionPool;
    private final ConfigManager configManager;

    public RedisService(ConfigManager configManager) {
        this.configManager = configManager;

        String redisHost = configManager.getRedisHost();
        int redisPort = configManager.getRedisPort();
        String redisPassword = configManager.getRedisPassword();

        RedisURI.Builder uriBuilder = RedisURI.builder()
                .withHost(redisHost)
                .withPort(redisPort)
                .withTimeout(Duration.ofSeconds(3));
        if (redisPassword != null && !redisPassword.isEmpty()) {
            uriBuilder.withPassword(redisPassword.toCharArray());
        }

        ClientResources clientResources = DefaultClientResources.builder().build();
        this.redisClient = RedisClient.create(clientResources, uriBuilder.build());

        // 使用连接池，多连接并发处理请求
        GenericObjectPoolConfig<StatefulRedisConnection<String, String>> poolConfig = new GenericObjectPoolConfig<>();
        int maxT = configManager.getRedisPoolMaxTotal();
        poolConfig.setMaxTotal(maxT);
        poolConfig.setMaxIdle(Math.max(2, maxT * 2 / 3));
        poolConfig.setMinIdle(Math.max(1, Math.min(8, maxT / 4)));
        poolConfig.setMaxWait(Duration.ofSeconds(2));
        poolConfig.setTestWhileIdle(true);

        this.connectionPool = ConnectionPoolSupport.createGenericObjectPool(
                () -> redisClient.connect(), poolConfig);

        logger.info("已连接到Redis(连接池模式): {}:{}", redisHost, redisPort);
    }

    /**
     * 从连接池获取一个同步命令接口
     */
    private RedisCommands<String, String> getSyncCommands() throws Exception {
        StatefulRedisConnection<String, String> conn = connectionPool.borrowObject();
        return conn.sync();
    }

    /**
     * 归还连接到连接池
     */
    private void returnConnection(StatefulRedisConnection<String, String> conn) {
        if (conn != null) {
            connectionPool.returnObject(conn);
        }
    }

    /**
     * 设置键值对并设置过期时间
     */
    public void setWithExpiry(String key, String value, int seconds) {
        StatefulRedisConnection<String, String> conn = null;
        try {
            conn = connectionPool.borrowObject();
            conn.sync().setex(key, seconds, value);
        } catch (Exception e) {
            logger.error("设置Redis键值对失败: {}", e.getMessage(), e);
        } finally {
            returnConnection(conn);
        }
    }

    /**
     * 获取键对应的值
     */
    public String get(String key) {
        StatefulRedisConnection<String, String> conn = null;
        try {
            conn = connectionPool.borrowObject();
            return conn.sync().get(key);
        } catch (Exception e) {
            logger.error("获取Redis键值失败: {}", e.getMessage(), e);
            return null;
        } finally {
            returnConnection(conn);
        }
    }

    /**
     * 删除键
     */
    public void del(String key) {
        StatefulRedisConnection<String, String> conn = null;
        try {
            conn = connectionPool.borrowObject();
            conn.sync().del(key);
        } catch (Exception e) {
            logger.error("删除Redis键失败: {}", e.getMessage(), e);
        } finally {
            returnConnection(conn);
        }
    }

    /**
     * 检查键是否存在
     */
    public boolean exists(String key) {
        StatefulRedisConnection<String, String> conn = null;
        try {
            conn = connectionPool.borrowObject();
            return conn.sync().exists(key) > 0;
        } catch (Exception e) {
            logger.error("检查Redis键存在性失败: {}", e.getMessage(), e);
            return false;
        } finally {
            returnConnection(conn);
        }
    }

    /**
     * 执行Lua脚本（原子操作，用于Rate Limit等场景）
     */
    public Object eval(String script, String[] keys, String[] args) {
        StatefulRedisConnection<String, String> conn = null;
        try {
            conn = connectionPool.borrowObject();
            return conn.sync().eval(script, io.lettuce.core.ScriptOutputType.INTEGER,
                    keys, args);
        } catch (Exception e) {
            logger.error("执行Redis Lua脚本失败: {}", e.getMessage(), e);
            return null;
        } finally {
            returnConnection(conn);
        }
    }

    public void sadd(String key, String member) {
        if (key == null || member == null || member.isEmpty()) {
            return;
        }
        StatefulRedisConnection<String, String> conn = null;
        try {
            conn = connectionPool.borrowObject();
            conn.sync().sadd(key, member);
        } catch (Exception e) {
            logger.error("Redis SADD 失败: {}", e.getMessage(), e);
        } finally {
            returnConnection(conn);
        }
    }

    public void srem(String key, String member) {
        if (key == null || member == null || member.isEmpty()) {
            return;
        }
        StatefulRedisConnection<String, String> conn = null;
        try {
            conn = connectionPool.borrowObject();
            conn.sync().srem(key, member);
        } catch (Exception e) {
            logger.error("Redis SREM 失败: {}", e.getMessage(), e);
        } finally {
            returnConnection(conn);
        }
    }

    public Set<String> smembers(String key) {
        if (key == null || key.isEmpty()) {
            return Collections.emptySet();
        }
        StatefulRedisConnection<String, String> conn = null;
        try {
            conn = connectionPool.borrowObject();
            Set<String> members = conn.sync().smembers(key);
            return members != null ? members : Collections.emptySet();
        } catch (Exception e) {
            logger.error("Redis SMEMBERS 失败: {}", e.getMessage(), e);
            return Collections.emptySet();
        } finally {
            returnConnection(conn);
        }
    }

    public void expire(String key, long seconds) {
        if (key == null || key.isEmpty() || seconds <= 0) {
            return;
        }
        StatefulRedisConnection<String, String> conn = null;
        try {
            conn = connectionPool.borrowObject();
            conn.sync().expire(key, seconds);
        } catch (Exception e) {
            logger.error("Redis EXPIRE 失败: {}", e.getMessage(), e);
        } finally {
            returnConnection(conn);
        }
    }

    /**
     * 获取键的剩余TTL（秒）
     */
    public long ttl(String key) {
        StatefulRedisConnection<String, String> conn = null;
        try {
            conn = connectionPool.borrowObject();
            return conn.sync().ttl(key);
        } catch (Exception e) {
            logger.error("获取Redis TTL失败: {}", e.getMessage(), e);
            return -1;
        } finally {
            returnConnection(conn);
        }
    }

    /**
     * 关闭连接池和客户端
     */
    public void close() {
        try {
            if (connectionPool != null) {
                connectionPool.close();
            }
            if (redisClient != null) {
                redisClient.shutdown();
            }
        } catch (Exception e) {
            logger.error("关闭Redis连接失败: {}", e.getMessage(), e);
        }
    }
}
