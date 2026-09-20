package com.neko.music.util;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * music 表 7 个预计算拼音检索列的绑定辅助。
 *
 * <p>此前 FileUploadHandler、MusicManagementHandler、AdminUploadAuditHandler、
 * AdminMusicIngestService 各自复制了一份相同的绑定逻辑，这里收敛为一份。
 *
 * <p>语义与原各处内联写法一致：title/artist 的完整拼音、中文首字母、英文词首字母，
 * 以及 album 的完整拼音（album 为 null 时写空串）。
 */
public final class MusicPinyinColumns {

    private MusicPinyinColumns() {
    }

    /**
     * 从 {@code firstIndex} 开始依次写入 7 列：
     * title_pinyin、title_pinyin_initials、title_word_initials、
     * artist_pinyin、artist_pinyin_initials、artist_word_initials、album_pinyin。
     */
    public static void bind(PreparedStatement stmt, int firstIndex, String title, String artist, String album)
            throws SQLException {
        stmt.setString(firstIndex, PinyinUtil.getPinyin(title));
        stmt.setString(firstIndex + 1, PinyinUtil.getPinyinInitials(title));
        stmt.setString(firstIndex + 2, PinyinUtil.getWordInitials(title));
        stmt.setString(firstIndex + 3, PinyinUtil.getPinyin(artist));
        stmt.setString(firstIndex + 4, PinyinUtil.getPinyinInitials(artist));
        stmt.setString(firstIndex + 5, PinyinUtil.getWordInitials(artist));
        stmt.setString(firstIndex + 6, album != null ? PinyinUtil.getPinyin(album) : "");
    }
}
