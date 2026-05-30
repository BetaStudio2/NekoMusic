package com.neko.music.service;

import com.neko.music.Main;
import com.neko.music.util.AudioFileValidator;
import com.neko.music.util.AudioIntegrityValidator;
import com.neko.music.util.LrcValidator;
import com.neko.music.util.MusicAdMetadataPatcher;
import com.neko.music.util.BatchMusicMatchUtil;
import com.neko.music.util.ChineseConverter;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 管理员后台入库：校验临时音频/歌词/封面后写入 Music 目录与数据库（与 FileUploadHandler 规则一致）。
 */
public class AdminMusicIngestService {
    private static final Logger logger = LoggerFactory.getLogger(AdminMusicIngestService.class);

    /** 批量搜索：有歌手时站内模糊匹配最低分 */
    private static final int MIN_BATCH_MATCH_SCORE_WITH_ARTIST = 100;
    /** 批量搜索：仅歌名时站内模糊匹配最低分 */
    private static final int MIN_BATCH_MATCH_SCORE_TITLE_ONLY = 90;
    private static final int BATCH_CANDIDATE_LIMIT = 80;

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

        Optional<IngestedMusic> duplicate = findExistingDuplicate(title, artist, albumVal);
        if (duplicate.isPresent()) {
            logger.info("跳过入库，曲库已有重复 id={}: title={} artist={}",
                    duplicate.get().id(), title, artist);
            return duplicate;
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
            if (com.neko.music.Main.getLyricsSearchIndex() != null) {
                com.neko.music.Main.getLyricsSearchIndex().rebuildOne(musicId);
            }

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
     * 批量搜索：先在站内找最匹配（精确 → 模糊打分），无合格结果再由调用方走网易云。
     */
    public Optional<IngestedMusic> findBestLocalMatchForBatchItem(String title, String artist) throws SQLException {
        Optional<IngestedMusic> exact = findExactMatch(title, artist);
        if (exact.isPresent()) {
            return exact;
        }
        if (title == null || title.isBlank()) {
            return Optional.empty();
        }
        String reqTitle = title.trim();
        String reqArtist = artist == null ? "" : artist.trim();

        List<IngestedMusic> candidates = loadBatchSearchCandidates(reqTitle, reqArtist);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        IngestedMusic best = null;
        int bestScore = 0;
        for (IngestedMusic candidate : candidates) {
            int score = scoreBatchItemMatch(candidate, reqTitle, reqArtist);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        int minScore = reqArtist.isEmpty() ? MIN_BATCH_MATCH_SCORE_TITLE_ONLY : MIN_BATCH_MATCH_SCORE_WITH_ARTIST;
        if (best != null && bestScore >= minScore) {
            logger.debug("批量站内模糊命中: title={} artist={} id={} score={}",
                    reqTitle, reqArtist, best.id(), bestScore);
            return Optional.of(best);
        }
        return Optional.empty();
    }

    /**
     * 按歌名（及可选歌手）精确匹配曲库，多条时取 id 最大的一条。
     * 仅歌名且无歌手时：同标题下必须唯一歌手，否则视为歧义不返回。
     */
    public Optional<IngestedMusic> findExactMatch(String title, String artist) throws SQLException {
        if (title == null || title.isBlank()) {
            return Optional.empty();
        }
        String reqTitle = title.trim();
        String reqArtist = artist == null ? "" : artist.trim();
        List<IngestedMusic> candidates = loadByTitleVariants(reqTitle);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        if (!reqArtist.isEmpty()) {
            List<IngestedMusic> matched = new ArrayList<>();
            for (IngestedMusic m : candidates) {
                if (BatchMusicMatchUtil.artistsRelate(reqArtist, m.artist())) {
                    matched.add(m);
                }
            }
            if (matched.size() == 1) {
                return Optional.of(matched.get(0));
            }
            if (matched.size() > 1) {
                IngestedMusic best = null;
                int bestScore = 0;
                for (IngestedMusic m : matched) {
                    int s = BatchMusicMatchUtil.scoreTitleContribution(reqTitle, m.title())
                            + BatchMusicMatchUtil.scoreArtistContribution(reqArtist, m.artist());
                    if (s > bestScore) {
                        bestScore = s;
                        best = m;
                    }
                }
                if (best != null && bestScore >= MIN_BATCH_MATCH_SCORE_WITH_ARTIST) {
                    return Optional.of(best);
                }
            }
            return Optional.empty();
        }
        Set<String> distinctArtists = new HashSet<>();
        for (IngestedMusic m : candidates) {
            distinctArtists.add(normalizeForExact(m.artist()));
        }
        if (distinctArtists.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(candidates.get(0));
    }

    private static String normalizeForExact(String s) {
        if (s == null) {
            return "";
        }
        return ChineseConverter.toSimplified(s.trim()).toLowerCase(Locale.ROOT);
    }

    private List<IngestedMusic> loadByTitleVariants(String title) throws SQLException {
        List<String> variants = ChineseConverter.getFullSearchVariants(title);
        if (variants.isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder(
                "SELECT id, title, artist, album, duration, upload_user_id, created_at FROM music WHERE ");
        List<String> conditions = new ArrayList<>();
        for (int i = 0; i < variants.size(); i++) {
            conditions.add("title = ?");
        }
        sql.append(String.join(" OR ", conditions));
        sql.append(" ORDER BY id DESC");

        List<IngestedMusic> rows = new ArrayList<>();
        try (Connection conn = Main.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            for (String variant : variants) {
                stmt.setString(idx++, variant);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String rowTitle = rs.getString("title");
                    if (rowTitle == null || !normalizeForExact(rowTitle).equals(normalizeForExact(title))) {
                        continue;
                    }
                    rows.add(mapIngestedRow(rs));
                }
            }
        }
        return rows;
    }

    private List<IngestedMusic> loadBatchSearchCandidates(String title, String artist) throws SQLException {
        List<String> titleVariants = ChineseConverter.getFullSearchVariants(title);
        if (titleVariants.isEmpty()) {
            return List.of();
        }

        List<String> conditions = new ArrayList<>();
        List<String> params = new ArrayList<>();

        for (String variant : titleVariants) {
            conditions.add("title LIKE ?");
            params.add("%" + variant + "%");
        }
        String coreTitle = BatchMusicMatchUtil.coreTitle(title);
        if (!coreTitle.isBlank() && !normalizeForExact(coreTitle).equals(normalizeForExact(title))) {
            for (String variant : ChineseConverter.getFullSearchVariants(coreTitle)) {
                conditions.add("title LIKE ?");
                params.add("%" + variant + "%");
            }
        }
        if (artist != null && !artist.isBlank()) {
            for (String variant : ChineseConverter.getFullSearchVariants(artist)) {
                conditions.add("artist LIKE ?");
                params.add("%" + variant + "%");
            }
            for (String token : BatchMusicMatchUtil.artistTokens(artist)) {
                if (token.length() >= 2) {
                    conditions.add("artist LIKE ?");
                    params.add("%" + token + "%");
                }
            }
        }

        String sql = "SELECT id, title, artist, album, duration, upload_user_id, created_at FROM music WHERE ("
                + String.join(" OR ", conditions)
                + ") ORDER BY id DESC LIMIT ?";

        List<IngestedMusic> rows = new ArrayList<>();
        try (Connection conn = Main.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            int idx = 1;
            for (String param : params) {
                stmt.setString(idx++, param);
            }
            stmt.setInt(idx, BATCH_CANDIDATE_LIMIT);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapIngestedRow(rs));
                }
            }
        }
        return rows;
    }

    /**
     * 批量条目站内匹配打分：歌名权重高于歌手，要求歌名至少有一定相似度。
     */
    static int scoreBatchItemMatch(IngestedMusic music, String reqTitle, String reqArtist) {
        if (music == null || reqTitle == null || reqTitle.isBlank()) {
            return 0;
        }
        int score = BatchMusicMatchUtil.scoreTitleContribution(reqTitle, music.title());
        if (score <= 0) {
            return 0;
        }
        score += BatchMusicMatchUtil.scoreArtistContribution(reqArtist, music.artist());
        return Math.max(score, 0);
    }

    private static IngestedMusic mapIngestedRow(ResultSet rs) throws SQLException {
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
        return findExistingDuplicate(title, artist, album).isPresent();
    }

    /**
     * 按规范化歌名 + 歌手关联（及同标题同专辑）查找曲库中已存在的同一首。
     * 用于网易云补全、上传入库等，避免「查询词不同但实为同一曲」重复写入。
     */
    public Optional<IngestedMusic> findExistingDuplicate(String title, String artist, String album)
            throws SQLException {
        if (title == null || title.isBlank()) {
            return Optional.empty();
        }
        String reqTitle = title.trim();
        String reqArtist = artist == null ? "" : artist.trim();
        String reqAlbum = album == null || album.isBlank() ? "" : album.trim();

        Map<Integer, IngestedMusic> candidates = new LinkedHashMap<>();
        for (IngestedMusic m : loadByTitleVariants(reqTitle)) {
            candidates.putIfAbsent(m.id(), m);
        }
        String coreTitle = BatchMusicMatchUtil.coreTitle(reqTitle);
        if (!coreTitle.equals(reqTitle)) {
            for (IngestedMusic m : loadByTitleVariants(coreTitle)) {
                candidates.putIfAbsent(m.id(), m);
            }
        }
        if (!reqArtist.isEmpty()) {
            for (IngestedMusic m : loadBatchSearchCandidates(reqTitle, reqArtist)) {
                candidates.putIfAbsent(m.id(), m);
            }
        }

        for (IngestedMusic m : candidates.values()) {
            if (!titlesMatchForDuplicate(reqTitle, m.title())) {
                continue;
            }
            if (!reqArtist.isEmpty()) {
                if (BatchMusicMatchUtil.artistsRelate(reqArtist, m.artist())) {
                    return Optional.of(m);
                }
                if (!reqAlbum.isEmpty() && reqAlbum.equals(m.album())) {
                    return Optional.of(m);
                }
                continue;
            }
            if (!reqAlbum.isEmpty() && reqAlbum.equals(m.album())) {
                return Optional.of(m);
            }
        }
        return Optional.empty();
    }

    private static boolean titlesMatchForDuplicate(String reqTitle, String dbTitle) {
        if (dbTitle == null || dbTitle.isBlank()) {
            return false;
        }
        String reqCore = normalizeForExact(BatchMusicMatchUtil.coreTitle(reqTitle));
        String dbCore = normalizeForExact(BatchMusicMatchUtil.coreTitle(dbTitle));
        if (!reqCore.isEmpty() && reqCore.equals(dbCore)) {
            return true;
        }
        return normalizeForExact(reqTitle).equals(normalizeForExact(dbTitle));
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
