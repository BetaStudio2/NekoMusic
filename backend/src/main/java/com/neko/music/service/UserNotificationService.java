package com.neko.music.service;

import com.neko.music.database.UserNotificationDatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 站内消息的写入与实时信号。
 *
 * <p>消息本体只存 MySQL；Redis 里只维护一个「每用户单调递增的版本号」，让 SSE 长连接用一次
 * O(1) 的 GET 就知道有没有新消息，避免每个在线连接都去轮询数据库。</p>
 *
 * <p>版本号只用于「变没变」的比较，不参与任何判定：Redis 故障时读到的一直是同一个值，
 * 表现为「不推送」而不是推错；断线期间的消息由客户端重连后按游标走列表接口补拉。</p>
 */
public class UserNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(UserNotificationService.class);

    private static final String VERSION_KEY_PREFIX = "notify:ver:";

    /** 版本号保留时长（秒）：长期没有新消息的用户自动回收这个键，避免键无限增长。 */
    private static final int VERSION_TTL_SECONDS = 7 * 24 * 60 * 60;

    /** 递增版本号并刷新过期时间，一次往返完成。 */
    private static final String BUMP_SCRIPT =
            "local v = redis.call('INCR', KEYS[1]) "
                    + "redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) "
                    + "return v";

    private final UserNotificationDatabaseManager databaseManager;
    private final RedisService redisService;

    public UserNotificationService(UserNotificationDatabaseManager databaseManager, RedisService redisService) {
        this.databaseManager = databaseManager;
        this.redisService = redisService;
    }

    /**
     * 写入一条站内消息并推进实时信号。
     *
     * @return 新消息 id；失败返回 -1，调用方不应因此中断自己的主流程
     */
    public int notify(int userId, String type, String title, String body, String link, Integer actorUserId) {
        int id = databaseManager.insert(userId, type, title, body, link, actorUserId);
        if (id > 0) {
            bumpVersion(userId);
        }
        return id;
    }

    /**
     * 读实时信号版本号：值发生变化即代表该用户有新消息待推送。
     * 没有历史写入时为 {@code 0}；Redis 不可用时也是 {@code 0}（同样表现为「不变」）。
     */
    public long currentVersion(int userId) {
        if (redisService == null) {
            return 0;
        }
        String raw = redisService.get(VERSION_KEY_PREFIX + userId);
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            logger.warn("站内消息版本号格式异常，按 0 处理: userId={}, raw={}", userId, raw);
            return 0;
        }
    }

    private void bumpVersion(int userId) {
        if (redisService == null) {
            return;
        }
        Object result = redisService.eval(BUMP_SCRIPT, new String[]{VERSION_KEY_PREFIX + userId},
                new String[]{String.valueOf(VERSION_TTL_SECONDS)});
        if (result == null) {
            logger.warn("推进站内消息版本号失败（Redis 不可用）: userId={}", userId);
        }
    }
}
