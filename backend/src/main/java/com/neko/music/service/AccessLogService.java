package com.neko.music.service;

import com.neko.music.database.LogDatabaseManager;
import com.neko.music.model.AccessLogRecord;
import com.neko.music.model.LogPage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 访问日志：记录**每一个**请求的量、来源与客户端信息，供安全分析与访问量统计。
 *
 * <p>写入异步批量（见 {@link LogBatchWriter}），不参与请求关键路径；读取供后台按条件检索与聚合。
 * 数据落在独立日志库，不共用 {@code neko_music}。</p>
 */
public class AccessLogService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 分页上限，避免一次把日志库整表捞出来。 */
    private static final int MAX_PAGE_SIZE = 200;
    private static final int DEFAULT_PAGE_SIZE = 50;
    /** 统计窗口上限（天）。 */
    private static final int MAX_STATS_DAYS = 90;
    /** Top 榜长度。 */
    private static final int TOP_LIMIT = 10;

    private static final String INSERT_SQL = """
            INSERT INTO access_log (occurred_at, method, path, query, category, status, duration_ms,
                response_bytes, client_ip, ip_subnet, user_agent, client_type, client_platform,
                client_version, neko_client, referer, origin, accept_language, authenticated,
                admin_id, admin_username, host, scheme, protocol, thread)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

    private static volatile AccessLogService current;

    private final LogDatabaseManager logDatabaseManager;
    private final LogBatchWriter<AccessLogRecord> writer;

    public AccessLogService(LogDatabaseManager logDatabaseManager) {
        this.logDatabaseManager = logDatabaseManager;
        this.writer = new LogBatchWriter<>("access-log", this::insertBatch);
    }

    public void start() {
        writer.start();
    }

    /** 停止写入线程并把队列里的存量刷盘。 */
    public void close() {
        writer.close();
    }

    /** 记录一条访问日志；未启用或队列满时静默丢弃，绝不阻塞请求。 */
    public static void record(AccessLogRecord record) {
        AccessLogService service = current;
        if (service != null) {
            service.writer.offer(record);
        }
    }

    public static AccessLogService current() {
        return current;
    }

    public static void setCurrent(AccessLogService service) {
        current = service;
    }

    private void insertBatch(List<AccessLogRecord> batch) throws SQLException {
        try (Connection conn = logDatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(INSERT_SQL)) {
            for (AccessLogRecord record : batch) {
                bindInsert(stmt, record);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private static void bindInsert(PreparedStatement stmt, AccessLogRecord r) throws SQLException {
        int i = 1;
        stmt.setLong(i++, r.occurredAt());
        stmt.setString(i++, LogQuerySupport.truncate(r.method(), 16));
        stmt.setString(i++, LogQuerySupport.truncate(r.path(), 512));
        stmt.setString(i++, LogQuerySupport.truncate(r.query(), 2048));
        stmt.setString(i++, LogQuerySupport.truncate(r.category(), 16));
        stmt.setInt(i++, r.status());
        stmt.setInt(i++, r.durationMs());
        stmt.setLong(i++, r.responseBytes());
        stmt.setString(i++, LogQuerySupport.truncate(r.clientIp(), 64));
        stmt.setString(i++, LogQuerySupport.truncate(r.ipSubnet(), 64));
        stmt.setString(i++, LogQuerySupport.truncate(r.userAgent(), 1024));
        stmt.setString(i++, LogQuerySupport.truncate(r.clientType(), 24));
        stmt.setString(i++, LogQuerySupport.truncate(r.clientPlatform(), 32));
        stmt.setString(i++, LogQuerySupport.truncate(r.clientVersion(), 64));
        stmt.setString(i++, LogQuerySupport.truncate(r.nekoClient(), 128));
        stmt.setString(i++, LogQuerySupport.truncate(r.referer(), 1024));
        stmt.setString(i++, LogQuerySupport.truncate(r.origin(), 255));
        stmt.setString(i++, LogQuerySupport.truncate(r.acceptLanguage(), 255));
        stmt.setBoolean(i++, r.authenticated());
        if (r.adminId() == null) {
            stmt.setNull(i++, Types.INTEGER);
        } else {
            stmt.setInt(i++, r.adminId());
        }
        stmt.setString(i++, LogQuerySupport.truncate(r.adminUsername(), 255));
        stmt.setString(i++, LogQuerySupport.truncate(r.host(), 255));
        stmt.setString(i++, LogQuerySupport.truncate(r.scheme(), 16));
        stmt.setString(i++, LogQuerySupport.truncate(r.protocol(), 16));
        stmt.setString(i, LogQuerySupport.truncate(r.thread(), 128));
    }

    /**
     * 按条件分页检索访问日志。
     *
     * @param ip        客户端 IP（模糊匹配）
     * @param path      路径（模糊匹配）
     * @param clientType 客户端类型（精确）
     * @param status    状态码（精确）
     * @param fromMs    起始时间（含），null 不限
     * @param toMs      结束时间（含），null 不限
     * @param keyword   关键字，命中 路径 / 查询串 / UA / IP / 管理员用户名 任一
     */
    public LogPage list(int page, int size, String ip, String path, String clientType, Integer status,
                        Long fromMs, Long toMs, String keyword) throws SQLException {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        appendEquals(where, params, "client_type", blankToNull(clientType));
        if (status != null) {
            where.append(" AND status = ?");
            params.add(status);
        }
        appendLike(where, params, "client_ip", blankToNull(ip));
        appendLike(where, params, "path", blankToNull(path));
        if (fromMs != null) {
            where.append(" AND occurred_at >= ?");
            params.add(fromMs);
        }
        if (toMs != null) {
            where.append(" AND occurred_at <= ?");
            params.add(toMs);
        }
        appendKeyword(where, params, blankToNull(keyword),
                "path", "query", "user_agent", "client_ip", "admin_username");

        long total;
        List<Map<String, Object>> items = new ArrayList<>();
        try (Connection conn = logDatabaseManager.getConnection()) {
            try (PreparedStatement countStmt = conn.prepareStatement("SELECT COUNT(*) FROM access_log" + where)) {
                bind(countStmt, params);
                try (ResultSet rs = countStmt.executeQuery()) {
                    total = rs.next() ? rs.getLong(1) : 0L;
                }
            }
            String sql = "SELECT * FROM access_log" + where + " ORDER BY id DESC LIMIT ? OFFSET ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                int index = bind(stmt, params);
                stmt.setInt(index++, safeSize);
                stmt.setInt(index, (safePage - 1) * safeSize);
                try (ResultSet rs = stmt.executeQuery()) {
                    items = LogQuerySupport.toMaps(rs);
                }
            }
        }
        return new LogPage(total, safePage, safeSize, items);
    }

    /** 访问量概览：总量、今日量、独立 IP、爬虫占比，以及最近 N 天的趋势与 Top 榜。 */
    public Map<String, Object> stats(int days) throws SQLException {
        int window = Math.min(Math.max(1, days), MAX_STATS_DAYS);
        long startOfToday = LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant().toEpochMilli();
        long windowStart = LocalDate.now(ZONE).minusDays(window - 1L).atStartOfDay(ZONE).toInstant().toEpochMilli();

        Map<String, Object> stats = new LinkedHashMap<>();
        try (Connection conn = logDatabaseManager.getConnection()) {
            stats.put("totalRequests", scalar(conn, "SELECT COUNT(*) FROM access_log", null));
            stats.put("todayRequests", scalar(conn, "SELECT COUNT(*) FROM access_log WHERE occurred_at >= ?",
                    startOfToday));
            stats.put("todayUniqueIps", scalar(conn,
                    "SELECT COUNT(DISTINCT client_ip) FROM access_log WHERE occurred_at >= ?", startOfToday));
            stats.put("todayBots", scalar(conn,
                    "SELECT COUNT(*) FROM access_log WHERE occurred_at >= ? AND client_type IN ('bot','scanner')",
                    startOfToday));
            stats.put("todayErrors", scalar(conn,
                    "SELECT COUNT(*) FROM access_log WHERE occurred_at >= ? AND status >= 400", startOfToday));
            stats.put("days", window);
            stats.put("dailyTrend", dailyTrend(conn, windowStart, window));
            stats.put("topIps", grouped(conn,
                    "SELECT client_ip AS k, COUNT(*) AS c FROM access_log WHERE occurred_at >= ? "
                            + "GROUP BY client_ip ORDER BY c DESC LIMIT " + TOP_LIMIT, windowStart));
            stats.put("topPaths", grouped(conn,
                    "SELECT path AS k, COUNT(*) AS c FROM access_log WHERE occurred_at >= ? "
                            + "GROUP BY path ORDER BY c DESC LIMIT " + TOP_LIMIT, windowStart));
            stats.put("clientTypes", countByQuery(conn,
                    "SELECT client_type AS k, COUNT(*) AS c FROM access_log WHERE occurred_at >= ? "
                            + "GROUP BY client_type ORDER BY c DESC", windowStart));
            stats.put("categories", countByQuery(conn,
                    "SELECT category AS k, COUNT(*) AS c FROM access_log WHERE occurred_at >= ? "
                            + "GROUP BY category ORDER BY c DESC", windowStart));
            stats.put("topUserAgents", grouped(conn,
                    "SELECT LEFT(COALESCE(user_agent,''), 255) AS k, COUNT(*) AS c FROM access_log "
                            + "WHERE occurred_at >= ? GROUP BY k ORDER BY c DESC LIMIT " + TOP_LIMIT, windowStart));
        }
        return stats;
    }

    /** 删除超过保留期的记录，返回删除条数。 */
    public int purgeOlderThan(int retentionDays) throws SQLException {
        if (retentionDays <= 0) {
            return 0;
        }
        long cutoff = LocalDate.now(ZONE).minusDays(retentionDays).atStartOfDay(ZONE).toInstant().toEpochMilli();
        try (Connection conn = logDatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM access_log WHERE occurred_at < ?")) {
            stmt.setLong(1, cutoff);
            return stmt.executeUpdate();
        }
    }

    /** 按天聚合的访问趋势；窗口内没有数据的日期补 0，保证前端折线不断点。 */
    private static List<Map<String, Object>> dailyTrend(Connection conn, long windowStart, int days)
            throws SQLException {
        Map<String, Long> counts = new LinkedHashMap<>();
        String sql = "SELECT DATE(FROM_UNIXTIME(occurred_at / 1000)) AS d, COUNT(*) AS c FROM access_log "
                + "WHERE occurred_at >= ? GROUP BY d";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, windowStart);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    counts.put(rs.getString("d"), rs.getLong("c"));
                }
            }
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        LocalDate today = LocalDate.now(ZONE);
        for (int i = days - 1; i >= 0; i--) {
            String date = today.minusDays(i).toString();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", date);
            row.put("count", counts.getOrDefault(date, 0L));
            trend.add(row);
        }
        return trend;
    }

    private static Map<String, Long> countByQuery(Connection conn, String sql, long param) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, param);
            try (ResultSet rs = stmt.executeQuery()) {
                return LogQuerySupport.countBy(rs, "k", "c");
            }
        }
    }

    private static long scalar(Connection conn, String sql, Long param) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (param != null) {
                stmt.setLong(1, param);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private static List<Map<String, Object>> grouped(Connection conn, String sql, long param) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, param);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("key", rs.getString("k"));
                    row.put("count", rs.getLong("c"));
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private static void appendEquals(StringBuilder where, List<Object> params, String column, String value) {
        if (value != null) {
            where.append(" AND ").append(column).append(" = ?");
            params.add(value);
        }
    }

    private static void appendLike(StringBuilder where, List<Object> params, String column, String value) {
        if (value != null) {
            where.append(" AND ").append(column).append(" LIKE ? ESCAPE '\\\\'");
            params.add(LogQuerySupport.likePattern(value));
        }
    }

    private static void appendKeyword(StringBuilder where, List<Object> params, String keyword, String... columns) {
        if (keyword == null) {
            return;
        }
        StringBuilder clause = new StringBuilder(" AND (");
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) {
                clause.append(" OR ");
            }
            clause.append(columns[i]).append(" LIKE ? ESCAPE '\\\\'");
            params.add(LogQuerySupport.likePattern(keyword));
        }
        clause.append(')');
        where.append(clause);
    }

    private static int bind(PreparedStatement stmt, List<Object> params) throws SQLException {
        int index = 1;
        for (Object param : params) {
            if (param instanceof Integer intValue) {
                stmt.setInt(index++, intValue);
            } else {
                stmt.setObject(index++, param);
            }
        }
        return index;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
