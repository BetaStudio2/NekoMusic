package com.neko.music.database;

import com.neko.music.model.VipPriceItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * VIP 价目表：使用主库 MySQL 表 {@code vip_pricing}，便于与业务库一并备份。
 */
public class VipPricingDatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(VipPricingDatabaseManager.class);
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final DatabaseManager databaseManager;

    public VipPricingDatabaseManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        try {
            seedIfEmpty();
        } catch (SQLException e) {
            logger.warn("VIP 价目表种子数据跳过: {}", e.getMessage());
        }
    }

    private void seedIfEmpty() throws SQLException {
        try (Connection conn = databaseManager.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) AS c FROM vip_pricing")) {
            if (rs.next() && rs.getInt("c") > 0) {
                return;
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("doesn't exist")) {
                return;
            }
            throw e;
        }
        String insert = "INSERT INTO vip_pricing (months, days, price_yuan, sort_order) VALUES (?,?,?,?)";
        try (Connection conn = databaseManager.getConnection(); PreparedStatement ps = conn.prepareStatement(insert)) {
            int[][] seeds = {{0, 7}, {1, 0}, {3, 0}, {12, 0}};
            double[] prices = {2.99, 9.99, 24.99, 88.99};
            for (int i = 0; i < seeds.length; i++) {
                ps.setInt(1, seeds[i][0]);
                ps.setInt(2, seeds[i][1]);
                ps.setBigDecimal(3, java.math.BigDecimal.valueOf(prices[i]));
                ps.setInt(4, i);
                ps.executeUpdate();
            }
            logger.info("已写入 VIP 价目表默认示例数据（MySQL）");
        }
    }

    public Optional<VipPriceItem> getById(int id) {
        String sql = "SELECT id, months, days, price_yuan, sort_order, updated_at FROM vip_pricing WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("按 id 读取 VIP 价目失败: {}", id, e);
        }
        return Optional.empty();
    }

    public List<VipPriceItem> listAll() {
        List<VipPriceItem> list = new ArrayList<>();
        String sql = "SELECT id, months, days, price_yuan, sort_order, updated_at FROM vip_pricing ORDER BY sort_order ASC, id ASC";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("读取 VIP 价目失败", e);
        }
        return list;
    }

    /**
     * 全量替换价目（事务内先清空再插入）。
     */
    public List<VipPriceItem> replaceAll(List<VipPriceItem> items) throws SQLException {
        String ins = "INSERT INTO vip_pricing (months, days, price_yuan, sort_order) VALUES (?,?,?,?)";
        try (Connection conn = databaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (Statement del = conn.createStatement()) {
                    del.executeUpdate("DELETE FROM vip_pricing");
                }
                try (PreparedStatement ps = conn.prepareStatement(ins)) {
                    int order = 0;
                    for (VipPriceItem it : items) {
                        ps.setInt(1, it.getMonths());
                        ps.setInt(2, it.getDays());
                        ps.setBigDecimal(3, java.math.BigDecimal.valueOf(it.getPriceYuan()));
                        ps.setInt(4, order++);
                        ps.executeUpdate();
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
        return listAll();
    }

    private VipPriceItem mapRow(ResultSet rs) throws SQLException {
        VipPriceItem it = new VipPriceItem();
        it.setId(rs.getInt("id"));
        it.setMonths(rs.getInt("months"));
        it.setDays(rs.getInt("days"));
        it.setPriceYuan(rs.getBigDecimal("price_yuan").doubleValue());
        it.setSortOrder(rs.getInt("sort_order"));
        Timestamp ts = rs.getTimestamp("updated_at");
        if (ts != null) {
            it.setUpdatedAt(ts.toInstant().atZone(ZoneId.of("Asia/Shanghai")).format(TS_FMT));
        } else {
            it.setUpdatedAt("");
        }
        return it;
    }
}
