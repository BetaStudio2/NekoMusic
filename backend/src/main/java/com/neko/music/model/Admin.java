package com.neko.music.model;

/**
 * 管理员。只读数据载体（JDBC 行映射后即使用）。
 *
 * <p>字段名与顺序与旧 POJO 一致；保留 {@code (username, passwordHash)}
 * 便捷构造器（默认 active=true、createdAt=now），供创建管理员使用。
 */
public record Admin(
        int id,
        String username,
        String passwordHash,
        String email,
        boolean active,
        String role,
        long createdAt,
        long lastLoginAt) {

    public Admin(String username, String passwordHash) {
        this(0, username, passwordHash, null, true, null, System.currentTimeMillis(), 0L);
    }
}
