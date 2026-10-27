package com.neko.music.database;

import com.neko.music.model.ClientApp;
import com.neko.music.model.ClientInstance;
import com.neko.music.util.ApkUploadValidator;
import com.neko.music.util.ClientIdentityProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 客户端应用 / 安装实例登记表。
 *
 * <p>官方客户端 ID 由代码种子写入；第三方客户端由后台管理接口创建。{@code client_id} 可公开，
 * 机密客户端的 secret 只存 SHA-256 哈希，明文仅在创建响应中出现一次。</p>
 */
public class ClientRegistryDatabaseManager {

    private static final Logger logger = LoggerFactory.getLogger(ClientRegistryDatabaseManager.class);

    public static final String OFFICIAL_WEB = "nkc_pub_web_prod";
    public static final String OFFICIAL_ANDROID = "nkc_pub_android_prod";
    public static final String OFFICIAL_ANDROID_WATCH = "nkc_pub_android_watch_prod";
    public static final String OFFICIAL_PC = "nkc_pub_pc_prod";

    private final DatabaseManager databaseManager;

    public ClientRegistryDatabaseManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /** 写入官方客户端登记；已存在时不覆盖后台修改过的状态与策略。 */
    public void seedOfficialApps() {
        String sql = """
            INSERT IGNORE INTO client_app
                (client_id, name, owner_label, platform, confidential, secret_hash, status,
                 official, attestation_required, allowed_origins, package_name, signing_digest,
                 created_at, updated_at, expires_at)
            VALUES (?, ?, ?, ?, 0, NULL, 'active', 1, ?, ?, ?, ?, ?, ?, NULL)
            """;
        long now = System.currentTimeMillis();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            seed(ps, OFFICIAL_WEB, "NekoMusic Web", "web", false,
                    null, null, null, now);
            seed(ps, OFFICIAL_ANDROID, "NekoMusic Android", "android", true,
                    null, "com.neko.music", ApkUploadValidator.EXPECTED_CERT_SHA256, now);
            seed(ps, OFFICIAL_ANDROID_WATCH, "NekoMusic Android Watch", "androidwatch", true,
                    null, "com.neko.music", ApkUploadValidator.EXPECTED_CERT_SHA256, now);
            seed(ps, OFFICIAL_PC, "NekoMusic PC", "pc", false,
                    null, null, null, now);
        } catch (SQLException e) {
            logger.warn("官方客户端登记种子写入失败: {}", e.getMessage());
        }
    }

    private static void seed(PreparedStatement ps, String clientId, String name, String platform,
                             boolean attestationRequired, String origins, String packageName,
                             String signingDigest, long now) throws SQLException {
        ps.setString(1, clientId);
        ps.setString(2, name);
        ps.setString(3, "NekoMusic");
        ps.setString(4, platform);
        ps.setBoolean(5, attestationRequired);
        ps.setString(6, origins);
        ps.setString(7, packageName);
        ps.setString(8, signingDigest);
        ps.setLong(9, now);
        ps.setLong(10, now);
        ps.executeUpdate();
    }

    public Optional<ClientApp> findApp(String clientId) {
        String sql = "SELECT * FROM client_app WHERE client_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapApp(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            logger.error("读取客户端应用失败 clientId={}", clientId, e);
            return Optional.empty();
        }
    }

    public List<ClientApp> listApps() {
        List<ClientApp> result = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT * FROM client_app ORDER BY official DESC, created_at ASC, id ASC")) {
            while (rs.next()) {
                result.add(mapApp(rs));
            }
        } catch (SQLException e) {
            logger.error("读取客户端应用列表失败", e);
        }
        return result;
    }

    public long createApp(ClientApp app) throws SQLException {
        String sql = """
            INSERT INTO client_app
                (client_id, name, owner_label, platform, confidential, secret_hash, status,
                 official, attestation_required, allowed_origins, package_name, signing_digest,
                 created_at, updated_at, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            long now = System.currentTimeMillis();
            ps.setString(1, app.clientId());
            ps.setString(2, app.name());
            ps.setString(3, app.ownerLabel());
            ps.setString(4, app.platform());
            ps.setBoolean(5, app.confidential());
            ps.setString(6, app.secretHash());
            ps.setString(7, app.status());
            ps.setBoolean(8, app.attestationRequired());
            ps.setString(9, app.allowedOrigins());
            ps.setString(10, app.packageName());
            ps.setString(11, app.signingDigest());
            ps.setLong(12, now);
            ps.setLong(13, now);
            if (app.expiresAt() == null) {
                ps.setNull(14, java.sql.Types.BIGINT);
            } else {
                ps.setLong(14, app.expiresAt());
            }
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1L;
            }
        }
    }

    public boolean updateStatus(String clientId, String status) {
        return executeUpdate("UPDATE client_app SET status = ?, updated_at = ? WHERE client_id = ?",
                ps -> {
                    ps.setString(1, status);
                    ps.setLong(2, System.currentTimeMillis());
                    ps.setString(3, clientId);
                });
    }

    public Optional<ClientInstance> findInstance(String instanceId) {
        String sql = "SELECT * FROM client_instance WHERE instance_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, instanceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapInstance(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            logger.error("读取客户端实例失败 instanceId={}", instanceId, e);
            return Optional.empty();
        }
    }

    /** 注册可重入：同一实例 ID + 同一公钥视为同一安装，更新最后活动时间。 */
    public boolean registerInstance(ClientInstance instance) throws SQLException {
        String sql = """
            INSERT INTO client_instance
                (instance_id, client_id, platform, public_key_spki, attestation_level,
                 app_id, signing_digest, status, created_at, last_seen_at, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'active', ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                last_seen_at = VALUES(last_seen_at),
                attestation_level = VALUES(attestation_level),
                app_id = VALUES(app_id),
                signing_digest = VALUES(signing_digest)
            """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, instance.instanceId());
            ps.setString(2, instance.clientId());
            ps.setString(3, instance.platform());
            ps.setString(4, instance.publicKeySpki());
            ps.setString(5, instance.attestationLevel());
            ps.setString(6, instance.appId());
            ps.setString(7, instance.signingDigest());
            ps.setLong(8, instance.createdAt());
            ps.setLong(9, instance.lastSeenAt());
            if (instance.expiresAt() == null) {
                ps.setNull(10, java.sql.Types.BIGINT);
            } else {
                ps.setLong(10, instance.expiresAt());
            }
            return ps.executeUpdate() > 0;
        }
    }

    public boolean touchInstance(String instanceId) {
        return executeUpdate("UPDATE client_instance SET last_seen_at = ? WHERE instance_id = ?",
                ps -> {
                    ps.setLong(1, System.currentTimeMillis());
                    ps.setString(2, instanceId);
                });
    }

    public boolean revokeInstance(String instanceId) {
        return executeUpdate("UPDATE client_instance SET status = 'revoked' WHERE instance_id = ?",
                ps -> ps.setString(1, instanceId));
    }

    public List<ClientInstance> listInstances(String clientId) {
        List<ClientInstance> result = new ArrayList<>();
        String sql = clientId == null || clientId.isBlank()
                ? "SELECT * FROM client_instance ORDER BY last_seen_at DESC LIMIT 500"
                : "SELECT * FROM client_instance WHERE client_id = ? ORDER BY last_seen_at DESC LIMIT 500";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (clientId != null && !clientId.isBlank()) {
                ps.setString(1, clientId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapInstance(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("读取客户端实例列表失败", e);
        }
        return result;
    }

    public boolean verifySecret(String clientId, String suppliedSecret) {
        if (clientId == null || suppliedSecret == null || suppliedSecret.isBlank()) {
            return false;
        }
        Optional<ClientApp> app = findApp(clientId);
        if (app.isEmpty() || app.get().secretHash() == null || app.get().secretHash().isBlank()) {
            return false;
        }
        String hash = "sha256:" + ClientIdentityProtocol.sha256Hex(
                suppliedSecret.getBytes(StandardCharsets.UTF_8));
        return ClientIdentityProtocol.constantTimeEquals(hash, app.get().secretHash());
    }

    public static String hashSecret(String secret) {
        return "sha256:" + ClientIdentityProtocol.sha256Hex(
                secret.getBytes(StandardCharsets.UTF_8));
    }

    private boolean executeUpdate(String sql, SqlBinder binder) {
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            binder.bind(ps);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("更新客户端登记失败", e);
            return false;
        }
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private static ClientApp mapApp(ResultSet rs) throws SQLException {
        long expires = rs.getLong("expires_at");
        Long expiresOrNull = rs.wasNull() ? null : expires;
        return new ClientApp(
                rs.getLong("id"),
                rs.getString("client_id"),
                rs.getString("name"),
                rs.getString("owner_label"),
                rs.getString("platform"),
                rs.getBoolean("confidential"),
                rs.getString("secret_hash"),
                rs.getString("status"),
                rs.getBoolean("official"),
                rs.getBoolean("attestation_required"),
                rs.getString("allowed_origins"),
                rs.getString("package_name"),
                rs.getString("signing_digest"),
                rs.getLong("created_at"),
                rs.getLong("updated_at"),
                expiresOrNull);
    }

    private static ClientInstance mapInstance(ResultSet rs) throws SQLException {
        long expires = rs.getLong("expires_at");
        Long expiresOrNull = rs.wasNull() ? null : expires;
        return new ClientInstance(
                rs.getLong("id"),
                rs.getString("instance_id"),
                rs.getString("client_id"),
                rs.getString("platform"),
                rs.getString("public_key_spki"),
                rs.getString("attestation_level"),
                rs.getString("app_id"),
                rs.getString("signing_digest"),
                rs.getString("status"),
                rs.getLong("created_at"),
                rs.getLong("last_seen_at"),
                expiresOrNull);
    }

}
