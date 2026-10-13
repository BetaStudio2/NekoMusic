package com.neko.music.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * 防重放加压来源标记：使用主库 MySQL 表 {@code replay_source_marks}。
 *
 * <p>为什么要有这张表：加压阶梯没有上限（见 {@code ReplayDifficultyPolicy}），持续踩线的来源最终会
 * 走到「题目对任何客户端都已不可解」的档位。此时继续发题只会连同出口的正常用户一起锁死，因此改为
 * **落库标记 + 明确拒绝**。落库而非只放 Redis，是为了让处置可见、可审计、可人工复核：谁在什么时候
 * 把哪个来源标了、峰值难度多少、窗口内多少请求，都留在业务库里随主库备份。</p>
 *
 * <p>表结构刻意保持简单：来源 IP 做主键（归一化小写），带 {@code expires_at} 让标记自动过期，
 * {@code marks} 累计复发次数，{@code peak_difficulty_bits} 记录历史峰值难度。标记是**临时**的
 * ——到期即失效，也可以由运维按 IP 直接删除。</p>
 *
 * <p>写失败只记日志（返回 {@code false}），读失败等同于「未标记」：标记层不可用不应把领取链路打死，
 * 与各限流服务的失败策略一致。</p>
 */
public class ReplaySourceMarkDatabaseManager {

    private static final Logger logger = LoggerFactory.getLogger(ReplaySourceMarkDatabaseManager.class);

    private static final String UPSERT = """
            INSERT INTO replay_source_marks
                (ip, marks, peak_difficulty_bits, window_requests, reason, first_marked_at, last_marked_at, expires_at)
            VALUES (?, 1, ?, ?, ?, NOW(), NOW(), DATE_ADD(NOW(), INTERVAL ? SECOND))
            ON DUPLICATE KEY UPDATE
                marks = marks + 1,
                peak_difficulty_bits = GREATEST(peak_difficulty_bits, VALUES(peak_difficulty_bits)),
                window_requests = VALUES(window_requests),
                reason = VALUES(reason),
                last_marked_at = NOW(),
                expires_at = VALUES(expires_at)
            """;

    private final DatabaseManager databaseManager;

    public ReplaySourceMarkDatabaseManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * 落一条标记（已存在则刷新并累计次数）。
     *
     * @param ttlSeconds 标记有效期；到期后该来源自动恢复可领取
     * @return 是否写入成功
     */
    public boolean upsert(String ip, int windowRequests, int difficultyBits, String reason, long ttlSeconds) {
        if (databaseManager == null || ip == null || ip.isBlank()) {
            return false;
        }
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPSERT)) {
            ps.setString(1, ip.trim().toLowerCase(java.util.Locale.ROOT));
            ps.setInt(2, difficultyBits);
            ps.setInt(3, windowRequests);
            ps.setString(4, reason == null ? "" : reason);
            ps.setLong(5, ttlSeconds);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.warn("写入防重放来源标记失败 ip={}: {}", ip, e.getMessage());
            return false;
        }
    }

    /** 该来源当前是否处于标记有效期内。 */
    public boolean isMarked(String ip) {
        if (databaseManager == null || ip == null || ip.isBlank()) {
            return false;
        }
        String sql = "SELECT 1 FROM replay_source_marks WHERE ip = ? AND expires_at > NOW()";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ip.trim().toLowerCase(java.util.Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.warn("读取防重放来源标记失败 ip={}（按未标记处理）: {}", ip, e.getMessage());
            return false;
        }
    }

    /** 当前有效标记（按最近标记时间倒序），供后台查看。 */
    public List<Mark> listActive(int limit) {
        List<Mark> marks = new ArrayList<>();
        if (databaseManager == null) {
            return marks;
        }
        String sql = """
                SELECT ip, marks, peak_difficulty_bits, window_requests, reason,
                       first_marked_at, last_marked_at, expires_at
                FROM replay_source_marks
                WHERE expires_at > NOW()
                ORDER BY last_marked_at DESC
                LIMIT ?
                """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, Math.min(limit, 500)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    marks.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.warn("列举防重放来源标记失败: {}", e.getMessage());
        }
        return marks;
    }

    /** 按来源删除标记（运维复核后放行）。 */
    public boolean clear(String ip) {
        if (databaseManager == null || ip == null || ip.isBlank()) {
            return false;
        }
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM replay_source_marks WHERE ip = ?")) {
            ps.setString(1, ip.trim().toLowerCase(java.util.Locale.ROOT));
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.warn("删除防重放来源标记失败 ip={}: {}", ip, e.getMessage());
            return false;
        }
    }

    /** 单条标记的记录。 */
    public record Mark(String ip, int marks, int peakDifficultyBits, int windowRequests, String reason,
                       Timestamp firstMarkedAt, Timestamp lastMarkedAt, Timestamp expiresAt) {
    }

    private static Mark mapRow(ResultSet rs) throws SQLException {
        return new Mark(
                rs.getString("ip"),
                rs.getInt("marks"),
                rs.getInt("peak_difficulty_bits"),
                rs.getInt("window_requests"),
                rs.getString("reason"),
                rs.getTimestamp("first_marked_at"),
                rs.getTimestamp("last_marked_at"),
                rs.getTimestamp("expires_at"));
    }
}
