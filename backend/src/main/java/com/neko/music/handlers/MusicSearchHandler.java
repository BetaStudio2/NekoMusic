package com.neko.music.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neko.music.Main;
import com.neko.music.util.MusicAssetLocator;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class MusicSearchHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(MusicSearchHandler.class);

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String requestBody = new String(request.getInputStream().readAllBytes(), "UTF-8");

        try {
            SearchRequest searchRequest = Main.getObjectMapper().readValue(requestBody, SearchRequest.class);
            List<Music> results = searchMusic(searchRequest.getQuery());

            Object responseResults = results.isEmpty() ? null : results;
            SearchResponse searchResponse = new SearchResponse(!results.isEmpty(),
                results.isEmpty() ? "未找到匹配的音乐" : "搜索成功",
                responseResults);

            response.setStatus(HttpStatus.OK_200);
            response.setContentType("application/json;charset=utf-8");
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(searchResponse));

        } catch (Exception e) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("请求格式错误: " + e.getMessage());
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
        }
    }

    /**
     * 使用预计算拼音列进行SQL筛选，避免全表加载到内存
     */
    private List<Music> searchMusic(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<Music> results = new ArrayList<>();
        int limit = 50;
        int fetchLimit = limit * 3;
        String queryLower = query.toLowerCase().trim();
        boolean containsPinyin = com.neko.music.util.PinyinUtil.isLikelyPinyin(query);

        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            String sql;
            List<String> params = new ArrayList<>();

            if (containsPinyin) {
                // 拼音搜索：利用预计算的拼音列在SQL层筛选
                // 匹配：标题/歌手/专辑的原文LIKE + 拼音列LIKE + 拼音首字母列LIKE + 词首字母列LIKE
                sql = "SELECT id, title, artist, album, duration, upload_user_id, created_at, " +
                      "title_pinyin, title_pinyin_initials, title_word_initials, artist_pinyin, artist_pinyin_initials, artist_word_initials, album_pinyin " +
                      "FROM music " +
                      "WHERE (title LIKE ? OR artist LIKE ? OR album LIKE ? " +
                      "OR title_pinyin LIKE ? OR title_pinyin_initials LIKE ? OR title_word_initials LIKE ? " +
                      "OR artist_pinyin LIKE ? OR artist_pinyin_initials LIKE ? OR artist_word_initials LIKE ? " +
                      "OR album_pinyin LIKE ?) " +
                      "ORDER BY created_at DESC LIMIT ?";
                String likeQuery = "%" + queryLower + "%";
                for (int i = 0; i < 10; i++) {
                    params.add(likeQuery);
                }
            } else {
                // 中文搜索：利用繁简体变体 + 预计算拼音列
                List<String> variants = com.neko.music.util.ChineseConverter.getFullSearchVariants(query);
                StringBuilder sqlBuilder = new StringBuilder();
                sqlBuilder.append("SELECT id, title, artist, album, duration, upload_user_id, created_at, ");
                sqlBuilder.append("title_pinyin, title_pinyin_initials, title_word_initials, artist_pinyin, artist_pinyin_initials, artist_word_initials, album_pinyin ");
                sqlBuilder.append("FROM music WHERE (");

                List<String> conditions = new ArrayList<>();
                // 原文变体匹配
                for (String variant : variants) {
                    conditions.add("(title LIKE ? OR artist LIKE ? OR album LIKE ?)");
                }
                // 拼音列匹配（应对用户输入拼音搜中文的场景）
                conditions.add("(title_pinyin LIKE ? OR title_pinyin_initials LIKE ? OR title_word_initials LIKE ? OR artist_pinyin LIKE ? OR artist_pinyin_initials LIKE ? OR artist_word_initials LIKE ? OR album_pinyin LIKE ?)");

                sqlBuilder.append(String.join(" OR ", conditions));
                sqlBuilder.append(") ORDER BY created_at DESC LIMIT ?");

                sql = sqlBuilder.toString();

                for (String variant : variants) {
                    String likeVariant = "%" + variant + "%";
                    params.add(likeVariant);
                    params.add(likeVariant);
                    params.add(likeVariant);
                }
                String likeQuery = "%" + queryLower + "%";
                for (int i = 0; i < 7; i++) {
                    params.add(likeQuery);
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.size(); i++) {
                    stmt.setString(i + 1, params.get(i));
                }
                stmt.setInt(params.size() + 1, fetchLimit);

                // SQL已经筛选了候选集，只需在内存中做精细打分排序
                List<ScoredMusic> scoredResults = new ArrayList<>();
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Music music = new Music();
                        music.setId(rs.getInt("id"));
                        music.setTitle(rs.getString("title") != null ? rs.getString("title") : "");
                        music.setArtist(rs.getString("artist") != null ? rs.getString("artist") : "");
                        music.setAlbum(rs.getString("album") != null ? rs.getString("album") : "");
                        music.setDuration(rs.getInt("duration"));
                        int mid = rs.getInt("id");
                        music.setFilePath(MusicAssetLocator.fileApiUrl(mid));
                        music.setCoverFilePath(MusicAssetLocator.coverApiUrl(mid));
                        music.setUploadUserId(rs.getInt("upload_user_id"));
                        music.setCreatedAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toString() : "");

                        // 读取预计算的拼音列
                        music.setTitlePinyin(rs.getString("title_pinyin"));
                        music.setTitlePinyinInitials(rs.getString("title_pinyin_initials"));
                        music.setTitleWordInitials(rs.getString("title_word_initials"));
                        music.setArtistPinyin(rs.getString("artist_pinyin"));
                        music.setArtistPinyinInitials(rs.getString("artist_pinyin_initials"));
                        music.setArtistWordInitials(rs.getString("artist_word_initials"));
                        music.setAlbumPinyin(rs.getString("album_pinyin"));

                        int score = calculateMatchScore(music, query);
                        if (score > 0) {
                            scoredResults.add(new ScoredMusic(music, score));
                        }
                    }
                }

                // 按分数排序
                scoredResults.sort((a, b) -> Integer.compare(b.score, a.score));

                for (int i = 0; i < Math.min(limit, scoredResults.size()); i++) {
                    results.add(scoredResults.get(i).music);
                }
            }

            logger.debug("搜索完成: query='{}', 结果数={}", query, results.size());

        } catch (Exception e) {
            logger.error("搜索音乐时出错", e);
        }

        return results;
    }

    /**
     * 计算匹配分数 - 使用预计算拼音列，避免运行时拼音转换
     */
    private int calculateMatchScore(Music music, String query) {
        if (query == null || query.isEmpty()) {
            return 0;
        }

        String queryLower = query.toLowerCase();
        String querySimplified = com.neko.music.util.ChineseConverter.toSimplified(query);
        int score = 0;

        // 检查标题
        String title = music.getTitle();
        if (title != null && !title.isEmpty()) {
            String titleLower = title.toLowerCase();
            String titleSimplified = com.neko.music.util.ChineseConverter.toSimplified(title).toLowerCase();

            if (titleLower.equals(queryLower)) {
                score += 100;
            } else if (titleLower.startsWith(queryLower)) {
                score += 80;
            } else if (titleLower.contains(queryLower)) {
                score += 60;
            } else if (titleSimplified.equals(queryLower) || titleLower.equals(querySimplified.toLowerCase())) {
                score += 95;
            } else if (titleSimplified.startsWith(queryLower) || titleLower.startsWith(querySimplified.toLowerCase())) {
                score += 75;
            } else if (titleSimplified.contains(queryLower) || titleLower.contains(querySimplified.toLowerCase())) {
                score += 55;
            }
        }

        // 检查歌手
        String artist = music.getArtist();
        if (artist != null && !artist.isEmpty()) {
            String artistLower = artist.toLowerCase();
            String artistSimplified = com.neko.music.util.ChineseConverter.toSimplified(artist).toLowerCase();

            if (artistLower.equals(queryLower)) {
                score += 50;
            } else if (artistLower.startsWith(queryLower)) {
                score += 40;
            } else if (artistLower.contains(queryLower)) {
                score += 30;
            } else if (artistSimplified.equals(queryLower) || artistLower.equals(querySimplified.toLowerCase())) {
                score += 45;
            } else if (artistSimplified.startsWith(queryLower) || artistLower.startsWith(querySimplified.toLowerCase())) {
                score += 35;
            } else if (artistSimplified.contains(queryLower) || artistLower.contains(querySimplified.toLowerCase())) {
                score += 25;
            }
        }

        // 检查专辑
        String album = music.getAlbum();
        if (album != null && !album.isEmpty()) {
            String albumLower = album.toLowerCase();
            String albumSimplified = com.neko.music.util.ChineseConverter.toSimplified(album).toLowerCase();

            if (albumLower.equals(queryLower)) {
                score += 20;
            } else if (albumLower.startsWith(queryLower)) {
                score += 15;
            } else if (albumLower.contains(queryLower)) {
                score += 10;
            } else if (albumSimplified.equals(queryLower) || albumLower.equals(querySimplified.toLowerCase())) {
                score += 18;
            } else if (albumSimplified.startsWith(queryLower) || albumLower.startsWith(querySimplified.toLowerCase())) {
                score += 13;
            } else if (albumSimplified.contains(queryLower) || albumLower.contains(querySimplified.toLowerCase())) {
                score += 8;
            }
        }

        // 拼音匹配 - 使用预计算列，不再运行时调用PinyinUtil
        // 取文本匹配和拼音匹配中的较高分
        int pinyinScore = matchPinyinScore(music, queryLower);
        if (pinyinScore > score) {
            score = pinyinScore;
        }

        return score;
    }

    /**
     * 使用预计算拼音列匹配，避免运行时拼音转换
     * 评分优先级：精确匹配 > 前缀匹配 > 子串匹配
     */
    private int matchPinyinScore(Music music, String queryLower) {
        int score = 0;

        // 标题拼音匹配
        String titlePinyin = music.getTitlePinyin();
        String titleInitials = music.getTitlePinyinInitials();
        String titleWordInitials = music.getTitleWordInitials();

        // 词首字母精确匹配最高优先级（如 "jhp" 匹配 "jhp"）
        if (titleWordInitials != null) {
            if (titleWordInitials.equals(queryLower)) {
                score += 95;
            } else if (titleWordInitials.startsWith(queryLower)) {
                score += 90;
            } else if (titleWordInitials.contains(queryLower)) {
                score += 80;
            }
        }
        if (score == 0 && titlePinyin != null && titlePinyin.contains(queryLower)) {
            score += 85;
        }
        if (score == 0 && titleInitials != null && titleInitials.contains(queryLower)) {
            score += 75;
        }

        // 歌手拼音匹配
        if (score == 0) {
            String artistPinyin = music.getArtistPinyin();
            String artistInitials = music.getArtistPinyinInitials();
            String artistWordInitials = music.getArtistWordInitials();

            if (artistWordInitials != null) {
                if (artistWordInitials.equals(queryLower)) {
                    score += 95;
                } else if (artistWordInitials.startsWith(queryLower)) {
                    score += 90;
                } else if (artistWordInitials.contains(queryLower)) {
                    score += 80;
                }
            }
            if (score == 0 && artistPinyin != null && artistPinyin.contains(queryLower)) {
                score += 85;
            }
            if (score == 0 && artistInitials != null && artistInitials.contains(queryLower)) {
                score += 75;
            }
        }

        // 专辑拼音匹配
        if (score == 0) {
            String albumPinyin = music.getAlbumPinyin();
            if (albumPinyin != null && albumPinyin.contains(queryLower)) {
                score += 90;
            }
        }

        return score;
    }

    // 辅助类：带分数的音乐
    private static class ScoredMusic {
        final Music music;
        final int score;
        ScoredMusic(Music music, int score) {
            this.music = music;
            this.score = score;
        }
    }

    // 内部类：搜索请求
    private static class SearchRequest {
        private String query;
        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
    }

    // 内部类：音乐对象（含拼音列）
    private static class Music {
        private int id;
        private String title;
        private String artist;
        private String album;
        private int duration;
        private String filePath;
        private String coverFilePath;
        private int uploadUserId;
        private String createdAt;
        // 预计算拼音列
        private String titlePinyin;
        private String titlePinyinInitials;
        private String titleWordInitials;
        private String artistPinyin;
        private String artistPinyinInitials;
        private String artistWordInitials;
        private String albumPinyin;

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getArtist() { return artist; }
        public void setArtist(String artist) { this.artist = artist; }
        public String getAlbum() { return album; }
        public void setAlbum(String album) { this.album = album; }
        public int getDuration() { return duration; }
        public void setDuration(int duration) { this.duration = duration; }
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        public String getCoverFilePath() { return coverFilePath; }
        public void setCoverFilePath(String coverFilePath) { this.coverFilePath = coverFilePath; }
        public int getUploadUserId() { return uploadUserId; }
        public void setUploadUserId(int uploadUserId) { this.uploadUserId = uploadUserId; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public String getTitlePinyin() { return titlePinyin; }
        public void setTitlePinyin(String titlePinyin) { this.titlePinyin = titlePinyin; }
        public String getTitlePinyinInitials() { return titlePinyinInitials; }
        public void setTitlePinyinInitials(String titlePinyinInitials) { this.titlePinyinInitials = titlePinyinInitials; }
        public String getTitleWordInitials() { return titleWordInitials; }
        public void setTitleWordInitials(String titleWordInitials) { this.titleWordInitials = titleWordInitials; }
        public String getArtistPinyin() { return artistPinyin; }
        public void setArtistPinyin(String artistPinyin) { this.artistPinyin = artistPinyin; }
        public String getArtistPinyinInitials() { return artistPinyinInitials; }
        public void setArtistPinyinInitials(String artistPinyinInitials) { this.artistPinyinInitials = artistPinyinInitials; }
        public String getArtistWordInitials() { return artistWordInitials; }
        public void setArtistWordInitials(String artistWordInitials) { this.artistWordInitials = artistWordInitials; }
        public String getAlbumPinyin() { return albumPinyin; }
        public void setAlbumPinyin(String albumPinyin) { this.albumPinyin = albumPinyin; }
    }

    private static class SearchResponse {
        private boolean success;
        private String message;
        private Object results;
        public SearchResponse(boolean success, String message, Object results) {
            this.success = success; this.message = message; this.results = results;
        }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public Object getResults() { return results; }
        public void setResults(Object results) { this.results = results; }
    }

    private static class ErrorResponse {
        private String error;
        public ErrorResponse(String error) { this.error = error; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }
}
