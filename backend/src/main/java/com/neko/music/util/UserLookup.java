package com.neko.music.util;

import com.neko.music.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 用户信息查询辅助。
 *
 * <p>原先前端歌单接口的处理器各自复制了一份昵称查询，收敛到这里。
 */
public final class UserLookup {

    private static final Logger logger = LoggerFactory.getLogger(UserLookup.class);

    private UserLookup() {
    }

    /** 按用户 id 查询昵称，查不到时返回「未知用户」。 */
    public static String getNickname(int userId) {
        String sql = "SELECT nickname FROM users WHERE id = ?";

        try (Connection conn = Main.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getString("nickname");
            }
        } catch (SQLException e) {
            logger.error("获取昵称失败: {}", e.getMessage(), e);
        }

        return "未知用户";
    }
}
