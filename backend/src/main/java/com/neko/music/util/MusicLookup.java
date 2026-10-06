package com.neko.music.util;

import com.neko.music.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * 音乐行查询辅助。
 *
 * <p>原先 {@code MusicCoverHandler} 与 {@code MusicFileHandler} 各复制了一份
 * {@code musicRowExists(int)}，收敛到这里。
 */
public final class MusicLookup {

    private static final Logger logger = LoggerFactory.getLogger(MusicLookup.class);

    private MusicLookup() {
    }

    /** 判断 music 表中是否存在指定 id 的记录。 */
    public static boolean musicRowExists(int musicId) {
        try (Connection conn = Main.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM music WHERE id = ? LIMIT 1")) {
            stmt.setInt(1, musicId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            logger.error("校验音乐记录时出错，音乐ID: {}", musicId, e);
            return false;
        }
    }

    /** 查询曲目最高音质（standard/hq/sq/hires）；不存在或未探测过返回 null。 */
    public static String findMaxQuality(int musicId) {
        try (Connection conn = Main.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT max_quality FROM music WHERE id = ?")) {
            stmt.setInt(1, musicId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString("max_quality") : null;
            }
        } catch (Exception e) {
            logger.error("查询音乐音质时出错，音乐ID: {}", musicId, e);
            return null;
        }
    }
}
