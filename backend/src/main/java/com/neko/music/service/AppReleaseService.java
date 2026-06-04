package com.neko.music.service;

import com.neko.music.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;

/** 客户端发布版本（单行表 app_release，供 /version.json 读取） */
public class AppReleaseService {
    private static final Logger logger = LoggerFactory.getLogger(AppReleaseService.class);

    public record AppRelease(String androidVer, String pcVer) {}

    public Optional<AppRelease> getRelease() {
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
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
        } catch (Exception e) {
            logger.error("读取 app_release 失败", e);
        }
        return Optional.empty();
    }

    public boolean upsertRelease(String androidVer, String pcVer) {
        String av = trimOrNull(androidVer);
        String pv = trimOrNull(pcVer);
        if (av == null || pv == null) {
            return false;
        }
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            boolean exists;
            try (PreparedStatement countStmt = conn.prepareStatement("SELECT 1 FROM app_release LIMIT 1");
                 ResultSet rs = countStmt.executeQuery()) {
                exists = rs.next();
            }
            String sql = exists
                    ? "UPDATE app_release SET android_ver = ?, pc_ver = ?"
                    : "INSERT INTO app_release (android_ver, pc_ver) VALUES (?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, av);
                stmt.setString(2, pv);
                return stmt.executeUpdate() > 0;
            }
        } catch (Exception e) {
            logger.error("写入 app_release 失败 android_ver={} pc_ver={}", av, pv, e);
            return false;
        }
    }

    private static String trimOrNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }
}
