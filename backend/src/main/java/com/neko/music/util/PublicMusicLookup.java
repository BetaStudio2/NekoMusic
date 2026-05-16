package com.neko.music.util;

import com.neko.music.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;

/** 对外公开的音乐元数据查询（无需登录） */
public final class PublicMusicLookup {
    private static final Logger logger = LoggerFactory.getLogger(PublicMusicLookup.class);

    private PublicMusicLookup() {}

    public static Optional<PublicMusic> findById(int musicId) {
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            String sql = "SELECT id, title, artist, album, duration, updated_at FROM music WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, musicId);
                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) {
                    return Optional.empty();
                }
                PublicMusic m = new PublicMusic();
                m.id = rs.getInt("id");
                m.title = rs.getString("title");
                m.artist = rs.getString("artist");
                m.album = rs.getString("album");
                m.duration = rs.getInt("duration");
                if (rs.getTimestamp("updated_at") != null) {
                    m.updatedAt = rs.getTimestamp("updated_at").toInstant().toString();
                }
                return Optional.of(m);
            }
        } catch (Exception e) {
            logger.error("查询音乐 id={} 失败", musicId, e);
            return Optional.empty();
        }
    }

    public static final class PublicMusic {
        public int id;
        public String title;
        public String artist;
        public String album;
        public int duration;
        public String updatedAt;
    }
}
