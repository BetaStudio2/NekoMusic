package com.neko.music.database;

import com.neko.music.config.ConfigManager;
import com.neko.music.config.SystemSettingDefinition;
import com.neko.music.config.SystemSettingRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统设置：主库 MySQL 表 {@code system_settings}（键值对）。
 *
 * <p>只负责存取，不理解业务含义；可写键由 {@link SystemSettingRegistry} 决定，未登记的键不落库。
 * 表不可用时一律返回空结果，让调用方回退到 config.yml 的值，不影响启动。</p>
 */
public class SystemSettingsDatabaseManager {

    private static final Logger logger = LoggerFactory.getLogger(SystemSettingsDatabaseManager.class);

    private final DatabaseManager databaseManager;

    public SystemSettingsDatabaseManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /** 读取全部设置；表缺失或读取失败时返回空表。 */
    public Map<String, String> loadAll() {
        Map<String, String> values = new LinkedHashMap<>();
        String sql = "SELECT setting_key, setting_value FROM system_settings";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String key = rs.getString("setting_key");
                if (key == null || key.isBlank()) {
                    continue;
                }
                String value = rs.getString("setting_value");
                values.put(key, value == null ? "" : value);
            }
        } catch (SQLException e) {
            logger.error("读取系统设置失败（本次回退为 config.yml 的值）: {}", e.getMessage());
        }
        return values;
    }

    /**
     * 首次启动：表里一条记录都没有时，把 config.yml 的当前生效值（缺失则取出厂默认值）灌进去。
     *
     * <p>迁移友好：老部署升级上来后行为与原来完全一致，管理员随后在后台改的就是这些值。</p>
     */
    public void seedIfEmpty(ConfigManager configManager) {
        try {
            if (countRows() > 0) {
                return;
            }
            Map<String, String> seeds = new LinkedHashMap<>();
            for (SystemSettingDefinition definition : SystemSettingRegistry.all()) {
                String value = definition.defaultValue();
                if (configManager != null) {
                    value = configManager.effectiveValue(definition.key()).orElse(value);
                }
                seeds.put(definition.key(), value == null ? "" : value);
            }
            int written = upsertAll(seeds, null);
            logger.info("系统设置表为空，已按 config.yml 现值写入 {} 条设置", written);
        } catch (SQLException e) {
            logger.error("初始化系统设置失败（继续使用 config.yml 的值）: {}", e.getMessage());
        }
    }

    /**
     * 批量写入（存在即更新）。
     *
     * @param values  键为点分路径，未在 {@link SystemSettingRegistry} 登记的键会被忽略
     * @param adminId 操作管理员，可为 null
     * @return 实际写入条数
     */
    public int upsertAll(Map<String, String> values, Integer adminId) throws SQLException {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        String sql = """
            INSERT INTO system_settings (setting_key, setting_value, updated_by)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value), updated_by = VALUES(updated_by)
            """;
        int written = 0;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                if (SystemSettingRegistry.find(entry.getKey()).isEmpty()) {
                    continue;
                }
                ps.setString(1, entry.getKey());
                ps.setString(2, entry.getValue() == null ? "" : entry.getValue());
                if (adminId == null) {
                    ps.setNull(3, Types.INTEGER);
                } else {
                    ps.setInt(3, adminId);
                }
                ps.addBatch();
                written++;
            }
            if (written > 0) {
                ps.executeBatch();
            }
        }
        return written;
    }

    /** 删除某条设置，使其回落到出厂默认值。 */
    public boolean delete(String key) throws SQLException {
        if (key == null || SystemSettingRegistry.find(key).isEmpty()) {
            return false;
        }
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM system_settings WHERE setting_key = ?")) {
            ps.setString(1, key);
            return ps.executeUpdate() > 0;
        }
    }

    private int countRows() throws SQLException {
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) AS c FROM system_settings");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt("c") : 0;
        }
    }
}
