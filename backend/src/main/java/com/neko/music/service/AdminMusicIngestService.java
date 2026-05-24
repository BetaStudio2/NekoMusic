package com.neko.music.service;

import com.neko.music.Main;
import com.neko.music.util.AudioFileValidator;
import com.neko.music.util.AudioIntegrityValidator;
import com.neko.music.util.LrcValidator;
import com.neko.music.util.MusicAdMetadataPatcher;
import com.neko.music.util.MusicAssetLocator;
import com.neko.music.util.PinyinUtil;
import com.neko.music.util.TempAudioSpool;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;

/**
 * 管理员后台入库：校验临时音频/歌词/封面后写入 Music 目录与数据库（与 FileUploadHandler 规则一致）。
 */
public class AdminMusicIngestService {
    private static final Logger logger = LoggerFactory.getLogger(AdminMusicIngestService.class);

    public record IngestedMusic(
            int id,
            String title,
            String artist,
            String album,
            int duration,
            int uploadUserId,
            String createdAt
    ) {}

    /**
     * @return 入库成功后的记录；若曲库已存在重复则 empty
     */
    public java.util.Optional<IngestedMusic> ingestFromTempFiles(
            Path musicTemp,
            Path coverTempOrNull,
            Path lyricsTemp,
            String title,
            String artist,
            String album,
            String language,
            String tags,
            int durationSec,
            Integer uploadUserId
    ) throws IOException, SQLException {
        String albumVal = album == null || album.isBlank() ? "未知专辑" : album.trim();
        String lang = language == null || language.isBlank() ? "未知语言" : language.trim();
        String tagVal = tags == null ? "" : tags.trim();

        if (isDuplicateMusic(title, artist, albumVal)) {
            logger.info("跳过入库，曲库已有重复: title={} artist={}", title, artist);
            return java.util.Optional.empty();
        }

        String ext = extensionFromPath(musicTemp);
        AudioFileValidator.FormatDetectionResult detection =
                AudioFileValidator.detectAndValidatePath(musicTemp, ext);
        if (!detection.isValid()) {
            throw new IOException("音频校验失败: " + detection.getErrorMessage());
        }

        String fileFormat = switch (detection.getFormat()) {
            case MP3 -> "mp3";
            case FLAC -> "flac";
            case WAV -> "wav";
            default -> throw new IOException("不支持的音频格式");
        };

        try (InputStream lrcIn = Files.newInputStream(lyricsTemp)) {
            long lrcSize = Files.size(lyricsTemp);
            LrcValidator.ValidationResult lrcResult = LrcValidator.validate(lrcIn, lrcSize);
            if (!lrcResult.isValid()) {
                throw new IOException("歌词校验失败: " + lrcResult.getErrorMessage());
            }
        }

        int duration = durationSec;
        if (duration <= 0) {
            duration = readAudioDurationSeconds(musicTemp);
        }

        AudioFileValidator.AudioFormat integrityFormat = switch (fileFormat) {
            case "flac" -> AudioFileValidator.AudioFormat.FLAC;
            case "wav" -> AudioFileValidator.AudioFormat.WAV;
            default -> AudioFileValidator.AudioFormat.MP3;
        };
        String integrityError = AudioIntegrityValidator.validateSavedFile(musicTemp, integrityFormat);
        if (integrityError != null) {
            throw new IOException(integrityError);
        }

        MusicAdMetadataPatcher.patchQuietly(musicTemp);

        int musicId = 0;
        String musicRelPath = null;
        try {
            musicId = insertMusicToDatabase(title, artist, albumVal, lang, tagVal, duration, uploadUserId, fileFormat);
            musicRelPath = MusicAssetLocator.AUDIO_REL_DIR + File.separator + musicId + "." + fileFormat;
            Path musicDest = Paths.get(musicRelPath);
            TempAudioSpool.commitReplace(musicTemp, musicDest);

            if (coverTempOrNull != null && Files.isRegularFile(coverTempOrNull)) {
                String coverExt = extensionFromPath(coverTempOrNull);
                String coverRelPath = MusicAssetLocator.COVER_REL_DIR + File.separator + musicId + "." + coverExt;
                Files.createDirectories(Paths.get(MusicAssetLocator.COVER_REL_DIR));
                Files.copy(coverTempOrNull, Paths.get(coverRelPath), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            Path lyricsDest = MusicAssetLocator.lyricsDir().resolve(musicId + ".lrc");
            Files.createDirectories(lyricsDest.getParent());
            Files.copy(lyricsTemp, lyricsDest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            IngestedMusic row = loadIngestedMusic(musicId);
            logger.info("网易云补全入库成功 id={} title={}", musicId, title);
            return java.util.Optional.of(row);
        } catch (Exception e) {
            if (musicId > 0) {
                deleteMusicRecordById(musicId);
                if (musicRelPath != null) {
                    Files.deleteIfExists(Paths.get(musicRelPath));
                }
                MusicAssetLocator.deleteCoverVariants(musicId);
                Files.deleteIfExists(MusicAssetLocator.lyricsDir().resolve(musicId + ".lrc"));
            }
            throw e;
        }
    }

    /**
     * 按搜索关键词在曲库中找最近一条可能匹配（用于补全并发时复用已入库结果）。
     */
    public Optional<IngestedMusic> findBestLocalMatchForQuery(String query) throws SQLException {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        String q = query.trim();
        String like = "%" + q + "%";
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            String sql = """
                    SELECT id, title, artist, album, duration, upload_user_id, created_at
                    FROM music
                    WHERE title LIKE ? OR artist LIKE ? OR album LIKE ?
                    ORDER BY id DESC
                    LIMIT 1
                    """;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, like);
                stmt.setString(2, like);
                stmt.setString(3, like);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    int uploadUserId = rs.getInt("upload_user_id");
                    if (rs.wasNull()) {
                        uploadUserId = 0;
                    }
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    return Optional.of(new IngestedMusic(
                            rs.getInt("id"),
                            rs.getString("title"),
                            rs.getString("artist"),
                            rs.getString("album"),
                            rs.getInt("duration"),
                            uploadUserId,
                            createdAt != null ? createdAt.toString() : ""
                    ));
                }
            }
        }
    }

    public boolean isDuplicateMusic(String title, String artist, String album) throws SQLException {
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            String sql = "SELECT artist, album FROM music WHERE title = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, title);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String existingArtist = rs.getString("artist");
                        String existingAlbum = rs.getString("album");
                        if (artist != null && artist.equals(existingArtist)) {
                            return true;
                        }
                        if (album != null && album.equals(existingAlbum)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static int insertMusicToDatabase(
            String title, String artist, String album, String language, String tags,
            int duration, Integer uploadUserId, String fileFormat
    ) throws SQLException {
        Integer validUploadUserId = null;
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            if (uploadUserId != null && isUserExists(conn, uploadUserId)) {
                validUploadUserId = uploadUserId;
            } else if (uploadUserId != null) {
                logger.warn("upload_user_id {} 不存在，将使用 NULL", uploadUserId);
            }

            String sql = """
                    INSERT INTO music (title, artist, album, language, tags, duration, file_format, upload_user_id,
                        title_pinyin, title_pinyin_initials, title_word_initials,
                        artist_pinyin, artist_pinyin_initials, artist_word_initials, album_pinyin)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            try (PreparedStatement stmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, title);
                stmt.setString(2, artist);
                stmt.setString(3, album);
                stmt.setString(4, language);
                stmt.setString(5, tags);
                stmt.setInt(6, duration);
                stmt.setString(7, fileFormat);
                stmt.setObject(8, validUploadUserId);
                stmt.setString(9, PinyinUtil.getPinyin(title));
                stmt.setString(10, PinyinUtil.getPinyinInitials(title));
                stmt.setString(11, PinyinUtil.getWordInitials(title));
                stmt.setString(12, PinyinUtil.getPinyin(artist));
                stmt.setString(13, PinyinUtil.getPinyinInitials(artist));
                stmt.setString(14, PinyinUtil.getWordInitials(artist));
                stmt.setString(15, PinyinUtil.getPinyin(album));
                int affected = stmt.executeUpdate();
                if (affected == 0) {
                    throw new SQLException("插入 music 失败");
                }
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getInt(1);
                    }
                }
                throw new SQLException("未获取到新音乐 ID");
            }
        }
    }

    private static IngestedMusic loadIngestedMusic(int musicId) throws SQLException {
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            String sql = "SELECT id, title, artist, album, duration, upload_user_id, created_at FROM music WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, musicId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("音乐记录不存在 id=" + musicId);
                    }
                    int uploadUserId = rs.getInt("upload_user_id");
                    if (rs.wasNull()) {
                        uploadUserId = 0;
                    }
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    return new IngestedMusic(
                            rs.getInt("id"),
                            rs.getString("title"),
                            rs.getString("artist"),
                            rs.getString("album"),
                            rs.getInt("duration"),
                            uploadUserId,
                            createdAt != null ? createdAt.toString() : ""
                    );
                }
            }
        }
    }

    private static boolean isUserExists(Connection conn, int userId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM users WHERE id = ?")) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static void deleteMusicRecordById(int musicId) {
        try (Connection conn = Main.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM music WHERE id = ?")) {
            stmt.setInt(1, musicId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("回滚删除 music 失败 id={}", musicId, e);
        }
    }

    private static int readAudioDurationSeconds(Path path) {
        try {
            AudioFile af = AudioFileIO.read(path.toFile());
            return af.getAudioHeader().getTrackLength();
        } catch (Exception e) {
            logger.warn("读取音频时长失败: {}", path, e);
            return 0;
        }
    }

    private static String extensionFromPath(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1).toLowerCase() : "mp3";
    }
}
