package com.neko.music.model;

import java.sql.Timestamp;

/**
 * 用户。只读数据载体（JDBC 行映射后即使用，不再修改）。
 *
 * <p>字段名与顺序与旧 POJO 一致；保留 {@code (username, password, email)}
 * 便捷构造器供注册使用。Jackson 对 boolean 组件的序列化名仍为
 * {@code emailVerified}（与原 {@code isEmailVerified()} 相同）。
 */
public record User(
        int id,
        String username,
        String password,
        String email,
        boolean emailVerified,
        String avatar,
        String createdAt,
        Timestamp vipExpiresAt) {

    public User(String username, String password, String email) {
        this(0, username, password, email, false, null, null, null);
    }
}
