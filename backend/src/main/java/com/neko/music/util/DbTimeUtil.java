package com.neko.music.util;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 数据库时间：写入优先用 {@link #SQL_NOW_SHANGHAI} 在 MySQL 内强制 UTC→东八区，不依赖 JVM/连接池时区。
 */
public final class DbTimeUtil {

    public static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    public static final String MYSQL_OFFSET = "+08:00";

    /**
     * 在 SQL 中强制得到东八区墙钟（用于 DATETIME 列 INSERT/UPDATE）。
     * <p>例：{@code INSERT INTO users (..., created_at) VALUES (..., SQL_NOW_SHANGHAI)}
     */
    public static final String SQL_NOW_SHANGHAI =
            "DATE_FORMAT(CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '+08:00'), '%Y-%m-%d %H:%i:%s')";

    public static final DateTimeFormatter MYSQL_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DbTimeUtil() {
    }

    /** Java 侧计算东八区墙钟（仅用于日志或展示，写入库请用 {@link #SQL_NOW_SHANGHAI}） */
    public static String nowShanghaiWallClock() {
        return ZonedDateTime.now(SHANGHAI).format(MYSQL_DATETIME);
    }

    public static String formatInShanghai(Timestamp ts) {
        if (ts == null) {
            return null;
        }
        return ts.toInstant().atZone(SHANGHAI).format(MYSQL_DATETIME);
    }

    public static String formatStoredWallClock(String rawFromDb) {
        if (rawFromDb == null || rawFromDb.isBlank()) {
            return rawFromDb;
        }
        String normalized = rawFromDb.length() >= 19 ? rawFromDb.substring(0, 19) : rawFromDb;
        return normalized.replace('T', ' ');
    }

    public static void ensureSessionTimeZone(java.sql.Connection conn) throws java.sql.SQLException {
        try (var stmt = conn.createStatement()) {
            stmt.execute("SET time_zone = '" + MYSQL_OFFSET + "'");
        }
    }
}
