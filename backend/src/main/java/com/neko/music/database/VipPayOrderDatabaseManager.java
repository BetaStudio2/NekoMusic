package com.neko.music.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * VIP 支付订单（ZPay）：创建待支付订单、异步通知幂等完成并延长 {@code users.vip_expires_at}。
 */
public class VipPayOrderDatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(VipPayOrderDatabaseManager.class);
    private static final ZoneId CN = ZoneId.of("Asia/Shanghai");

    public enum CompleteResult {
        /** 订单不存在或金额不匹配 */
        INVALID,
        /** 已处理过（重复通知） */
        ALREADY_DONE,
        /** 本次完成支付并发放会员时长 */
        COMPLETED
    }

    private final DatabaseManager databaseManager;

    public VipPayOrderDatabaseManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void insertPending(String outTradeNo, int userId, int pricingId, int months, int days,
                              BigDecimal money, String payType) throws SQLException {
        String sql = """
                INSERT INTO vip_pay_orders (out_trade_no, user_id, pricing_id, months, days, money, pay_type, status)
                VALUES (?,?,?,?,?,?,?,'pending')
                """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, outTradeNo);
            ps.setInt(2, userId);
            ps.setInt(3, pricingId);
            ps.setInt(4, months);
            ps.setInt(5, days);
            ps.setBigDecimal(6, money);
            ps.setString(7, payType);
            ps.executeUpdate();
        }
    }

    public void deletePending(String outTradeNo) {
        String sql = "DELETE FROM vip_pay_orders WHERE out_trade_no = ? AND status = 'pending'";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, outTradeNo);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warn("删除待支付订单失败 {}: {}", outTradeNo, e.getMessage());
        }
    }

    public CompleteResult tryCompletePaidOrder(String outTradeNo, String zpayTradeNo, String moneyFromNotify) {
        BigDecimal paid;
        try {
            paid = new BigDecimal(moneyFromNotify).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            logger.warn("异步通知金额无效: {}", moneyFromNotify);
            return CompleteResult.INVALID;
        }

        try (Connection conn = databaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Optional<Row> row = lockOrder(conn, outTradeNo);
                if (row.isEmpty()) {
                    conn.rollback();
                    return CompleteResult.INVALID;
                }
                Row r = row.get();
                if (r.money.setScale(2, RoundingMode.HALF_UP).compareTo(paid) != 0) {
                    logger.warn("订单金额与通知不一致 out={} expect={} got={}", outTradeNo, r.money, paid);
                    conn.rollback();
                    return CompleteResult.INVALID;
                }
                if (!"pending".equals(r.status)) {
                    conn.commit();
                    return CompleteResult.ALREADY_DONE;
                }

                int u = markPaid(conn, outTradeNo, zpayTradeNo);
                if (u != 1) {
                    conn.rollback();
                    return CompleteResult.ALREADY_DONE;
                }
                extendVip(conn, r.userId, r.months, r.days);
                conn.commit();
                logger.info("VIP 支付完成 userId={} out={} zpayTradeNo={}", r.userId, outTradeNo, zpayTradeNo);
                return CompleteResult.COMPLETED;
            } catch (SQLException e) {
                conn.rollback();
                logger.error("完成 VIP 订单事务失败: {}", outTradeNo, e);
                return CompleteResult.INVALID;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("完成 VIP 订单失败: {}", outTradeNo, e);
            return CompleteResult.INVALID;
        }
    }

    private Optional<Row> lockOrder(Connection conn, String outTradeNo) throws SQLException {
        String sql = """
                SELECT user_id, months, days, money, status
                FROM vip_pay_orders
                WHERE out_trade_no = ?
                FOR UPDATE
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, outTradeNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Row r = new Row();
                r.userId = rs.getInt("user_id");
                r.months = rs.getInt("months");
                r.days = rs.getInt("days");
                r.money = rs.getBigDecimal("money");
                r.status = rs.getString("status");
                return Optional.of(r);
            }
        }
    }

    private static int markPaid(Connection conn, String outTradeNo, String zpayTradeNo) throws SQLException {
        String sql = """
                UPDATE vip_pay_orders
                SET status = 'paid', zpay_trade_no = ?, paid_at = NOW()
                WHERE out_trade_no = ? AND status = 'pending'
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, zpayTradeNo == null ? "" : zpayTradeNo);
            ps.setString(2, outTradeNo);
            return ps.executeUpdate();
        }
    }

    private void extendVip(Connection conn, int userId, int months, int days) throws SQLException {
        Timestamp newEnd;
        try (PreparedStatement ps = conn.prepareStatement("SELECT vip_expires_at FROM users WHERE id = ? FOR UPDATE")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("用户不存在: " + userId);
                }
                Timestamp cur = rs.getTimestamp("vip_expires_at");
                ZonedDateTime base = ZonedDateTime.now(CN);
                if (cur != null && !rs.wasNull() && cur.getTime() > System.currentTimeMillis()) {
                    base = cur.toInstant().atZone(CN);
                }
                ZonedDateTime end = base.plusMonths(months).plusDays(days);
                newEnd = Timestamp.from(end.toInstant());
            }
        }
        try (PreparedStatement up = conn.prepareStatement("UPDATE users SET vip_expires_at = ? WHERE id = ?")) {
            up.setTimestamp(1, newEnd);
            up.setInt(2, userId);
            if (up.executeUpdate() != 1) {
                throw new SQLException("更新 VIP 失败 user=" + userId);
            }
        }
    }

    private static final class Row {
        int userId;
        int months;
        int days;
        BigDecimal money;
        String status;
    }

    /** 生成商户订单号（≤32 位，字母数字） */
    public static String newOutTradeNo() {
        String base = "NM" + System.currentTimeMillis();
        int rnd = (int) (Math.random() * 9000) + 1000;
        String s = base + rnd;
        if (s.length() > 32) {
            s = s.substring(0, 32);
        }
        return s;
    }

    public static String formatMoney(double yuan) {
        return String.format(Locale.US, "%.2f", yuan);
    }
}
