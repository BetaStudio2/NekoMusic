package com.neko.music.service;

import com.neko.music.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 播放次数统计：在响应之后异步累加，避免请求线程等待 Redis 往返与数据库提交。
 */
public final class PlayCountService {
    private static final Logger logger = LoggerFactory.getLogger(PlayCountService.class);

    /**
     * 队列满即丢弃——播放计数是尽力而为的统计，不值得让请求排队。
     */
    private static final ExecutorService EXECUTOR = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1024),
            Thread.ofPlatform().daemon().name("play-count-", 0).factory(),
            new ThreadPoolExecutor.DiscardPolicy());

    private PlayCountService() {
    }

    /**
     * 记录一次播放；IP 必须在请求线程内解析完成后传入。
     */
    public static void recordPlay(int musicId, String ipAddress) {
        EXECUTOR.execute(() -> incrementPlayCount(musicId, ipAddress));
    }

    private static void incrementPlayCount(int musicId, String ipAddress) {
        try {
            if (ipAddress == null || ipAddress.isEmpty()) {
                logger.warn("无法获取客户端IP地址，跳过播放次数统计");
                return;
            }

            String redisKey = "play_log:" + musicId + ":" + ipAddress;
            if (Main.getRedisService().exists(redisKey)) {
                logger.debug("同一IP {} 在一分钟内已播放过音乐ID {}，跳过播放次数统计", ipAddress, musicId);
                return;
            }

            try (Connection conn = Main.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "UPDATE music SET play_count = play_count + 1 WHERE id = ?")) {
                stmt.setInt(1, musicId);
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected > 0) {
                    logger.info("已增加音乐ID {} 的播放次数 (IP: {})", musicId, ipAddress);
                } else {
                    logger.warn("未找到音乐ID {}，无法更新播放次数", musicId);
                }
            }

            Main.getRedisService().setWithExpiry(redisKey, "1", 60);
        } catch (Exception e) {
            logger.error("增加播放次数时出错: {}", e.getMessage(), e);
        }
    }
}
