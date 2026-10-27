package com.neko.music.model;

import java.sql.Timestamp;

/**
 * 管理端展示的管理员账号。刻意不包含口令哈希等认证字段。
 */
public record AdminAccount(
        int id,
        String username,
        String email,
        String role,
        String registerTime) {

    public static AdminAccount from(Admin admin) {
        return new AdminAccount(
                admin.id(),
                admin.username(),
                admin.email(),
                admin.role(),
                new Timestamp(admin.createdAt()).toString());
    }
}
