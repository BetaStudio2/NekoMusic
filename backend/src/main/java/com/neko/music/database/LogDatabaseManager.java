package com.neko.music.database;

import com.neko.music.config.ConfigManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;

/**
 * 日志库：访问日志与操作日志的**独立 MySQL 库**（不共用 {@code neko_music} 主库）。
 *
 * <p>日志写入量远大于业务写入，且需要保留原始 UA / 路径明细做安全分析；放进主库会污染业务库的
 * 备份、慢查询与容量规划。因此这里单独建库、单独连池，主库只保留业务数据。</p>
 *
 * <p>库名取自 {@code system_settings} 的 {@code logging.database}；留空则回落到「主库名 + {@code _logs}」。
 * 库不存在时用主库连接执行 {@code CREATE DATABASE IF NOT EXISTS}（需要账号具备该权限）。
 * 任何一步失败都不会让服务启动失败：日志能力降级为「仅写应用日志」，业务不受影响。</p>
 */
public class LogDatabaseManager {

    private static final Logger logger = LoggerFactory.getLogger(LogDatabaseManager.class);

    /** 合法的库名：只允许字母、数字、下划线，避免拼接 DDL 时被注入。 */
    private static final Pattern VALID_DATABASE_NAME = Pattern.compile("[A-Za-z0-9_]{1,64}");

    /** 日志库连接池：写入走批量异步，池不需要很大，避免与主库抢占连接预算。 */
    private static final int POOL_MAX_SIZE = 8;
    private static final int POOL_MIN_IDLE = 2;

    private final ConfigManager configManager;
    private final String databaseName;
    private HikariDataSource dataSource;

    public LogDatabaseManager(ConfigManager configManager, String databaseName) {
        this.configManager = configManager;
        this.databaseName = databaseName;
    }

    /**
     * 解析日志库名：优先用配置值，留空则用「主库名 + {@code _logs}」；非法时返回空串（调用方降级）。
     */
    public static String resolveDatabaseName(ConfigManager configManager) {
        String configured = configManager.getLoggingDatabase();
        String name = (configured == null || configured.isBlank())
                ? configManager.getMysqlDatabase() + "_logs"
                : configured.trim();
        if (!VALID_DATABASE_NAME.matcher(name).matches()) {
            logger.error("日志库名不合法（只允许字母/数字/下划线），已停用日志库: {}", name);
            return "";
        }
        return name;
    }

    /** 用主库连接确保日志库存在（需要账号具备 CREATE 权限）。 */
    public static void ensureDatabaseExists(DatabaseManager mainDatabase, String databaseName) throws SQLException {
        String ddl = "CREATE DATABASE IF NOT EXISTS `" + databaseName
                + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci";
        try (Connection conn = mainDatabase.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(ddl);
        }
    }

    /** 打开日志库连接池并建表。 */
    public void init() throws SQLException {
        HikariConfig poolConfig = new HikariConfig();
        poolConfig.setJdbcUrl("jdbc:mysql://" + configManager.getMysqlHost() + ":" +
                configManager.getMysqlPort() + "/" + databaseName +
                "?useSSL=false&serverTimezone=Asia/Shanghai&connectionTimeZone=Asia/Shanghai" +
                "&forceConnectionTimeZoneToSession=true&allowPublicKeyRetrieval=true" +
                "&cachePrepStmts=true&prepStmtCacheSize=250&prepStmtCacheSqlLimit=2048&useServerPrepStmts=true");
        poolConfig.setUsername(configManager.getMysqlUsername());
        poolConfig.setPassword(configManager.getMysqlPassword());
        poolConfig.setMaximumPoolSize(POOL_MAX_SIZE);
        poolConfig.setMinimumIdle(POOL_MIN_IDLE);
        poolConfig.setPoolName("neko-log-pool");
        poolConfig.setConnectionTimeout(30000);
        poolConfig.setIdleTimeout(600000);
        poolConfig.setMaxLifetime(1800000);
        poolConfig.setConnectionInitSql("SET time_zone = '+08:00'");
        this.dataSource = new HikariDataSource(poolConfig);
        initializeTables();
    }

    private void initializeTables() throws SQLException {
        String accessLogTable = """
            CREATE TABLE IF NOT EXISTS access_log (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                occurred_at BIGINT NOT NULL COMMENT '请求时间（epoch 毫秒）',
                method VARCHAR(16) NOT NULL,
                path VARCHAR(512) NOT NULL,
                query VARCHAR(2048) NULL,
                category VARCHAR(16) NOT NULL COMMENT 'api/static/media/page/admin/other',
                status INT NOT NULL,
                duration_ms INT NOT NULL,
                response_bytes BIGINT NOT NULL DEFAULT 0,
                client_ip VARCHAR(64) NOT NULL,
                ip_subnet VARCHAR(64) NULL COMMENT 'IPv4 /24 或 IPv6 /48，便于聚合同一网段',
                user_agent VARCHAR(1024) NULL,
                client_type VARCHAR(24) NOT NULL COMMENT 'browser/official_client/third_party/bot/scanner/unknown',
                client_platform VARCHAR(32) NULL,
                client_version VARCHAR(64) NULL,
                neko_client VARCHAR(128) NULL COMMENT 'X-Neko-Client 头原值',
                referer VARCHAR(1024) NULL,
                origin VARCHAR(255) NULL,
                accept_language VARCHAR(255) NULL,
                authenticated TINYINT(1) NOT NULL DEFAULT 0,
                admin_id INT NULL,
                admin_username VARCHAR(255) NULL,
                host VARCHAR(255) NULL,
                scheme VARCHAR(16) NULL,
                protocol VARCHAR(16) NULL,
                thread VARCHAR(128) NULL,
                pow_difficulty INT NULL COMMENT '本次请求对应的 PoW 难度（比特）',
                pow_status VARCHAR(24) NULL COMMENT 'PoW 结果标签：verified/missing/issued/...',
                seo_render TINYINT(1) NULL COMMENT '本次是否返回服务端 SEO 渲染 HTML；与 SEO 无关为 NULL',
                KEY idx_access_occurred (occurred_at),
                KEY idx_access_ip (client_ip),
                KEY idx_access_path (path(191)),
                KEY idx_access_status (status),
                KEY idx_access_type (client_type),
                KEY idx_access_pow (pow_status)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;

        String operationLogTable = """
            CREATE TABLE IF NOT EXISTS operation_log (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                occurred_at BIGINT NOT NULL COMMENT '操作时间（epoch 毫秒）',
                admin_id INT NULL,
                admin_username VARCHAR(255) NULL,
                admin_role VARCHAR(32) NULL,
                module VARCHAR(64) NULL COMMENT '业务模块，如「用户管理」',
                action VARCHAR(64) NULL,
                method VARCHAR(16) NOT NULL,
                path VARCHAR(512) NOT NULL,
                query VARCHAR(2048) NULL,
                status INT NOT NULL,
                success TINYINT(1) NOT NULL DEFAULT 0,
                duration_ms INT NOT NULL,
                client_ip VARCHAR(64) NOT NULL,
                user_agent VARCHAR(1024) NULL,
                client_platform VARCHAR(32) NULL,
                detail VARCHAR(1024) NULL,
                KEY idx_operation_occurred (occurred_at),
                KEY idx_operation_admin (admin_id),
                KEY idx_operation_module (module)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(accessLogTable);
            stmt.execute(operationLogTable);
            migrate(conn);
            logger.info("日志库 {} 已就绪（access_log / operation_log）", databaseName);
        }
    }

    /**
     * 老库升级：{@code CREATE TABLE IF NOT EXISTS} 不会给已存在的表补列，新增字段必须显式迁移。
     *
     * <p>MySQL 不支持 {@code ADD COLUMN IF NOT EXISTS}（那是 MariaDB 的扩展），因此先查
     * {@code information_schema} 再决定是否执行 DDL；整个过程幂等，可在每次启动时安全重跑。</p>
     */
    private static void migrate(Connection conn) throws SQLException {
        ensureColumn(conn, "access_log", "pow_difficulty", "INT NULL COMMENT '本次请求对应的 PoW 难度（比特）'");
        ensureColumn(conn, "access_log", "pow_status",
                "VARCHAR(24) NULL COMMENT 'PoW 结果标签：verified/missing/issued/...'");
        ensureIndex(conn, "access_log", "idx_access_pow", "pow_status");
        ensureColumn(conn, "access_log", "seo_render",
                "TINYINT(1) NULL COMMENT '本次是否返回服务端 SEO 渲染 HTML；与 SEO 无关为 NULL'");
    }

    private static void ensureColumn(Connection conn, String table, String column, String definition)
            throws SQLException {
        String check = "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?";
        try (PreparedStatement stmt = conn.prepareStatement(check)) {
            stmt.setString(1, table);
            stmt.setString(2, column);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getLong(1) > 0) {
                    return;
                }
            }
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + definition);
            logger.info("日志库补列: {}.{}", table, column);
        }
    }

    private static void ensureIndex(Connection conn, String table, String index, String column)
            throws SQLException {
        String check = "SELECT COUNT(*) FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?";
        try (PreparedStatement stmt = conn.prepareStatement(check)) {
            stmt.setString(1, table);
            stmt.setString(2, index);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getLong(1) > 0) {
                    return;
                }
            }
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE `" + table + "` ADD INDEX `" + index + "` (`" + column + "`)");
            logger.info("日志库补索引: {}.{}", table, index);
        }
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
