package com.neko.music.service;

import com.neko.music.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

/** 客户端发布版本：对外 version.json 延迟生效，预留安装包上传时间 */
public class AppReleaseService {
    private static final Logger logger = LoggerFactory.getLogger(AppReleaseService.class);
    private static final ZoneId CN_ZONE = ZoneId.of("Asia/Shanghai");
    /** version.json 在保存新版本号后延迟生效的分钟数 */
    public static final int VERSION_JSON_DELAY_MINUTES = 30;

    public record AppRelease(String androidVer, String pcVer) {}

    public record AppReleaseState(
            AppRelease published,
            AppRelease pending,
            Instant pendingEffectiveAt
    ) {
        public AppRelease targetForUpload() {
            if (pending != null) {
                return pending;
            }
            return published;
        }

        public boolean hasPending() {
            return pending != null && pendingEffectiveAt != null;
        }
    }

    /** 供 /version.json：始终返回当前已对外生效的版本（必要时自动 promote 到期 pending） */
    public Optional<AppRelease> getPublishedReleaseForClients() {
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            promotePendingIfDue(conn);
            return loadPublished(conn);
        } catch (Exception e) {
            logger.error("读取对外版本失败", e);
            return Optional.empty();
        }
    }

    /** 供管理后台：含待生效版本与生效时间 */
    public Optional<AppReleaseState> getReleaseStateForAdmin() {
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            return loadState(conn, false);
        } catch (Exception e) {
            logger.error("读取 app_release 状态失败", e);
            return Optional.empty();
        }
    }

    /** 供上传安装包：优先使用待生效版本对应的文件名 */
    public Optional<AppRelease> getTargetReleaseForUpload() {
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            Optional<AppReleaseState> state = loadState(conn, false);
            if (state.isEmpty() || state.get().targetForUpload() == null) {
                return Optional.empty();
            }
            AppRelease target = state.get().targetForUpload();
            if (target.androidVer() == null || target.pcVer() == null) {
                return Optional.empty();
            }
            return Optional.of(target);
        } catch (Exception e) {
            logger.error("读取上传目标版本失败", e);
            return Optional.empty();
        }
    }

    /**
     * 保存新版本号：若尚无对外版本则立即生效；否则写入 pending，{@value #VERSION_JSON_DELAY_MINUTES} 分钟后对外生效。
     */
    public Optional<AppReleaseState> scheduleReleaseUpdate(String androidVer, String pcVer) {
        String av = trimOrNull(androidVer);
        String pv = trimOrNull(pcVer);
        if (av == null || pv == null) {
            return Optional.empty();
        }
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            Optional<AppRelease> published = loadPublished(conn);
            if (published.isEmpty()) {
                upsertFirstRelease(conn, av, pv);
                logger.info("首次发布客户端版本 androidVer={} pcVer={}", av, pv);
                return loadState(conn, false);
            }

            Instant effectiveAt = ZonedDateTime.now(CN_ZONE)
                    .plusMinutes(VERSION_JSON_DELAY_MINUTES)
                    .toInstant();
            String sql = """
                UPDATE app_release SET
                    pending_android_ver = ?,
                    pending_pc_ver = ?,
                    pending_effective_at = ?
                """;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, av);
                stmt.setString(2, pv);
                stmt.setTimestamp(3, Timestamp.from(effectiveAt));
                if (stmt.executeUpdate() <= 0) {
                    return Optional.empty();
                }
            }
            logger.info("已排期客户端版本 androidVer={} pcVer={} 将于 {} 对外生效",
                    av, pv, ZonedDateTime.ofInstant(effectiveAt, CN_ZONE));
            return loadState(conn, false);
        } catch (Exception e) {
            logger.error("排期 app_release 失败 android_ver={} pc_ver={}", av, pv, e);
            return Optional.empty();
        }
    }

    private void promotePendingIfDue(Connection conn) throws Exception {
        Optional<AppReleaseState> state = loadState(conn, true);
        if (state.isEmpty() || !state.get().hasPending()) {
            return;
        }
        Instant effectiveAt = state.get().pendingEffectiveAt();
        if (effectiveAt == null || Instant.now().isBefore(effectiveAt)) {
            return;
        }
        AppRelease pending = state.get().pending();
        if (pending == null) {
            return;
        }
        String sql = """
            UPDATE app_release SET
                android_ver = ?,
                pc_ver = ?,
                pending_android_ver = NULL,
                pending_pc_ver = NULL,
                pending_effective_at = NULL
            """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, pending.androidVer());
            stmt.setString(2, pending.pcVer());
            stmt.executeUpdate();
        }
        logger.info("待生效版本已自动发布 androidVer={} pcVer={}",
                pending.androidVer(), pending.pcVer());
    }

    private void upsertFirstRelease(Connection conn, String av, String pv) throws Exception {
        boolean exists;
        try (PreparedStatement countStmt = conn.prepareStatement("SELECT 1 FROM app_release LIMIT 1");
             ResultSet rs = countStmt.executeQuery()) {
            exists = rs.next();
        }
        String sql = exists
                ? """
                UPDATE app_release SET
                    android_ver = ?, pc_ver = ?,
                    pending_android_ver = NULL, pending_pc_ver = NULL, pending_effective_at = NULL
                """
                : "INSERT INTO app_release (android_ver, pc_ver) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, av);
            stmt.setString(2, pv);
            stmt.executeUpdate();
        }
    }

    private Optional<AppRelease> loadPublished(Connection conn) throws Exception {
        String sql = "SELECT android_ver, pc_ver FROM app_release LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                String androidVer = trimOrNull(rs.getString("android_ver"));
                String pcVer = trimOrNull(rs.getString("pc_ver"));
                if (androidVer != null && pcVer != null) {
                    return Optional.of(new AppRelease(androidVer, pcVer));
                }
            }
        }
        return Optional.empty();
    }

    private Optional<AppReleaseState> loadState(Connection conn, boolean forPromoteCheck) throws Exception {
        String sql = """
            SELECT android_ver, pc_ver,
                   pending_android_ver, pending_pc_ver, pending_effective_at
            FROM app_release LIMIT 1
            """;
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (!rs.next()) {
                return Optional.empty();
            }
            String pubAv = trimOrNull(rs.getString("android_ver"));
            String pubPv = trimOrNull(rs.getString("pc_ver"));
            if (pubAv == null || pubPv == null) {
                return Optional.empty();
            }
            AppRelease published = new AppRelease(pubAv, pubPv);

            String pendAv = trimOrNull(rs.getString("pending_android_ver"));
            String pendPv = trimOrNull(rs.getString("pending_pc_ver"));
            Timestamp ts = rs.getTimestamp("pending_effective_at");
            Instant effectiveAt = ts != null ? ts.toInstant() : null;

            AppRelease pending = null;
            if (pendAv != null && pendPv != null) {
                pending = new AppRelease(pendAv, pendPv);
            } else if (!forPromoteCheck) {
                pending = null;
                effectiveAt = null;
            }

            return Optional.of(new AppReleaseState(published, pending, effectiveAt));
        }
    }

    private static String trimOrNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }
}
