package com.neko.music.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neko.music.Main;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class MusicSearchHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(MusicSearchHandler.class);
    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 读取请求体
        StringBuilder requestBody = new StringBuilder();
        try (InputStream inputStream = request.getInputStream()) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                requestBody.append(new String(buffer, 0, bytesRead));
            }
        }

        try {
            // 解析JSON请求体
            SearchRequest searchRequest = objectMapper.readValue(requestBody.toString(), SearchRequest.class);
            
            // 搜索音乐
            List<Music> results = searchMusic(searchRequest.getQuery());
            
            // 如果没有搜索结果，返回null
            Object responseResults = results.isEmpty() ? null : results;
            
            // 返回结果
            SearchResponse searchResponse = new SearchResponse(!results.isEmpty(), 
                results.isEmpty() ? "未找到匹配的音乐" : "搜索成功", 
                responseResults);
            
            response.setStatus(HttpStatus.OK_200);
            response.setContentType("application/json;charset=utf-8");
            response.getWriter().println(objectMapper.writeValueAsString(searchResponse));
            
        } catch (Exception e) {
            // JSON解析错误或其他异常
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("请求格式错误: " + e.getMessage());
            response.getWriter().println(objectMapper.writeValueAsString(errorResponse));
        }
    }
    
    private List<Music> searchMusic(String query) {
        logger.info("=== searchMusic 方法开始执行 ===");
        logger.info("查询字符串: query='{}', 长度={}", query, query != null ? query.length() : 0);
        
        List<Music> results = new ArrayList<>();
        int limit = 50; // 设置默认限制
        
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            // 判断查询是否包含拼音（纯拼音或混合输入）
            boolean containsPinyin = com.neko.music.util.PinyinUtil.isLikelyPinyin(query);
            logger.info("是否包含拼音: containsPinyin={}", containsPinyin);
            
            List<Music> allMusic = new ArrayList<>();
            
            if (containsPinyin) {
                // 如果包含拼音，查询所有音乐（在应用层面进行拼音匹配）
                String sql = "SELECT id, title, artist, album, duration, file_path, cover_path, upload_user_id, created_at " +
                           "FROM music " +
                           "ORDER BY created_at DESC";
                
                logger.info("拼音搜索模式: 查询所有音乐");
                
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    ResultSet rs = stmt.executeQuery();
                    int index = 0;
                    
                    while (rs.next()) {
                        Music music = new Music();
                        music.setId(rs.getInt("id"));
                        music.setTitle(rs.getString("title") != null ? rs.getString("title") : "");
                        music.setArtist(rs.getString("artist") != null ? rs.getString("artist") : "");
                        music.setAlbum(rs.getString("album") != null ? rs.getString("album") : "");
                        music.setDuration(rs.getInt("duration"));
                        music.setFilePath(rs.getString("file_path") != null ? rs.getString("file_path") : "");
                        music.setCoverFilePath(rs.getString("cover_path") != null ? rs.getString("cover_path") : "");
                        music.setUploadUserId(rs.getInt("upload_user_id"));
                        music.setCreatedAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toString() : "");
                        
                        allMusic.add(music);
                        
                        // 记录前10条音乐
                        if (index < 10) {
                            logger.info("音乐记录 {}: title={}, artist={}", index + 1, music.getTitle(), music.getArtist());
                        }
                        index++;
                    }
                }
                
                logger.info("查询到 {} 条音乐记录，开始匹配", allMusic.size());
                
                // 在内存中进行混合匹配（拼音+中文），并计算匹配分数
                List<java.util.AbstractMap.SimpleEntry<Music, Integer>> scoredResults = new ArrayList<>();
                int matchCount = 0;
                
                for (Music music : allMusic) {
                    int score = calculateMatchScore(music, query);
                    if (score > 0) {
                        matchCount++;
                        scoredResults.add(new java.util.AbstractMap.SimpleEntry<>(music, score));
                        logger.info("匹配成功: title={}, artist={}, score={}", music.getTitle(), music.getArtist(), score);
                    }
                }
                
                // 按分数排序，分数高的在前
                scoredResults.sort((a, b) -> b.getValue().compareTo(a.getValue()));
                
                // 取前 limit 条结果
                for (int i = 0; i < Math.min(limit, scoredResults.size()); i++) {
                    results.add(scoredResults.get(i).getKey());
                    logger.info("结果 {}: title={}, artist={}, score={}", 
                        i + 1, 
                        scoredResults.get(i).getKey().getTitle(), 
                        scoredResults.get(i).getKey().getArtist(), 
                        scoredResults.get(i).getValue());
                }
                
                logger.info("拼音搜索完成: 检查了 {} 条音乐，匹配到 {} 条", allMusic.size(), matchCount);
                
            } else {
                // 如果不是拼音，使用正常的繁简体搜索
                List<String> variants = com.neko.music.util.ChineseConverter.getFullSearchVariants(query);
                logger.info("搜索变体: query={}, variants={}", query, variants);
                
                // 构建 SQL 查询，支持繁简体搜索
                StringBuilder sqlBuilder = new StringBuilder();
                sqlBuilder.append("SELECT id, title, artist, album, duration, file_path, cover_path, upload_user_id, created_at ");
                sqlBuilder.append("FROM music ");
                sqlBuilder.append("WHERE (");
                
                List<String> conditions = new ArrayList<>();
                for (int i = 0; i < variants.size(); i++) {
                    conditions.add("(title LIKE ? OR artist LIKE ? OR album LIKE ?)");
                }
                sqlBuilder.append(String.join(" OR ", conditions));
                sqlBuilder.append(") ");
                // 移除 ORDER BY created_at DESC，改为在内存中按分数排序
                sqlBuilder.append("LIMIT ?");
                
                logger.info("SQL查询: {}", sqlBuilder.toString());

                try (PreparedStatement stmt = conn.prepareStatement(sqlBuilder.toString())) {
                    // 设置参数
                    int paramIndex = 1;
                    for (String variant : variants) {
                        stmt.setString(paramIndex++, "%" + variant + "%");
                        stmt.setString(paramIndex++, "%" + variant + "%");
                        stmt.setString(paramIndex++, "%" + variant + "%");
                    }
                    // 这里设置一个较大的限制，确保能获取所有匹配的记录
                    stmt.setInt(paramIndex, 1000);
                    
                    ResultSet rs = stmt.executeQuery();
                    
                    while (rs.next()) {
                        Music music = new Music();
                        music.setId(rs.getInt("id"));
                        music.setTitle(rs.getString("title") != null ? rs.getString("title") : "");
                        music.setArtist(rs.getString("artist") != null ? rs.getString("artist") : "");
                        music.setAlbum(rs.getString("album") != null ? rs.getString("album") : "");
                        music.setDuration(rs.getInt("duration"));
                        music.setFilePath(rs.getString("file_path") != null ? rs.getString("file_path") : "");
                        music.setCoverFilePath(rs.getString("cover_path") != null ? rs.getString("cover_path") : "");
                        music.setUploadUserId(rs.getInt("upload_user_id"));
                        music.setCreatedAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toString() : "");
                        
                        allMusic.add(music);
                    }
                }
                
                logger.info("找到 {} 条音乐记录，开始计算分数", allMusic.size());
                
                // 计算每条记录的匹配分数
                List<java.util.AbstractMap.SimpleEntry<Music, Integer>> scoredResults = new ArrayList<>();
                for (Music music : allMusic) {
                    int score = calculateMatchScore(music, query);
                    if (score > 0) {
                        scoredResults.add(new java.util.AbstractMap.SimpleEntry<>(music, score));
                        logger.info("匹配: title={}, artist={}, score={}", 
                            music.getTitle(), music.getArtist(), score);
                    }
                }
                
                // 按分数排序，分数高的在前
                scoredResults.sort((a, b) -> b.getValue().compareTo(a.getValue()));
                
                // 取前 limit 条结果
                for (int i = 0; i < Math.min(limit, scoredResults.size()); i++) {
                    results.add(scoredResults.get(i).getKey());
                    logger.info("结果 {}: title={}, artist={}, score={}", 
                        i + 1, 
                        scoredResults.get(i).getKey().getTitle(), 
                        scoredResults.get(i).getKey().getArtist(), 
                        scoredResults.get(i).getValue());
                }
                
                logger.info("找到 {} 条音乐记录，最终返回 {} 条", allMusic.size(), results.size());
            }
        } catch (Exception e) {
            logger.error("搜索音乐时出错", e);
        }
        
        return results;
    }
    
    /**
     * 计算音乐的匹配分数
     * 分数越高，匹配度越高
     * 评分规则：
     * - 标题精确匹配（不区分大小写，支持繁简体）：100分
     * - 标题以查询开头：80分
     * - 标题包含查询：60分
     * - 歌手精确匹配：50分
     * - 歌手以查询开头：40分
     * - 歌手包含查询：30分
     * - 专辑精确匹配：20分
     * - 专辑以查询开头：15分
     * - 专辑包含查询：10分
     * - 拼音匹配：90分
     */
    private int calculateMatchScore(Music music, String query) {
        if (query == null || query.isEmpty()) {
            return 0;
        }
        
        String queryLower = query.toLowerCase();
        String querySimplified = com.neko.music.util.ChineseConverter.toSimplified(query);
        int score = 0;

        logger.info("计算分数: title={}, artist={}, album={}, query={}", 
            music.getTitle(), music.getArtist(), music.getAlbum(), query);
        logger.info("queryLower={}, querySimplified={}", queryLower, querySimplified);

        // 检查标题
        String title = music.getTitle();
        if (title != null && !title.isEmpty()) {
            String titleLower = title.toLowerCase();
            String titleSimplified = com.neko.music.util.ChineseConverter.toSimplified(title).toLowerCase();

            logger.info("标题检查: titleLower={}, titleSimplified={}", titleLower, titleSimplified);

            // 先检查原始文本匹配
            if (titleLower.equals(queryLower)) {
                score += 100; // 标题精确匹配
                logger.info("匹配: 标题精确匹配 +100");
            } else if (titleLower.startsWith(queryLower)) {
                score += 80; // 标题以查询开头
                logger.info("匹配: 标题以查询开头 +80");
            } else if (titleLower.contains(queryLower)) {
                score += 60; // 标题包含查询
                logger.info("匹配: 标题包含查询 +60");
            }
            // 如果原始文本没有匹配，检查繁简体转换后的匹配
            else if (titleSimplified.equals(queryLower) || titleLower.equals(querySimplified.toLowerCase())) {
                score += 95; // 繁简体精确匹配
                logger.info("匹配: 繁简体标题精确匹配 +95");
            } else if (titleSimplified.startsWith(queryLower) || titleLower.startsWith(querySimplified.toLowerCase())) {
                score += 75; // 繁简体前缀匹配
                logger.info("匹配: 繁简体标题前缀匹配 +75");
            } else if (titleSimplified.contains(queryLower) || titleLower.contains(querySimplified.toLowerCase())) {
                score += 55; // 繁简体包含匹配
                logger.info("匹配: 繁简体标题包含匹配 +55");
            }
        }

        // 检查歌手
        String artist = music.getArtist();
        if (artist != null && !artist.isEmpty()) {
            String artistLower = artist.toLowerCase();
            String artistSimplified = com.neko.music.util.ChineseConverter.toSimplified(artist).toLowerCase();

            logger.info("歌手检查: artistLower={}, artistSimplified={}", artistLower, artistSimplified);

            if (artistLower.equals(queryLower)) {
                score += 50; // 歌手精确匹配
                logger.info("匹配: 歌手精确匹配 +50");
            } else if (artistLower.startsWith(queryLower)) {
                score += 40; // 歌手以查询开头
                logger.info("匹配: 歌手以查询开头 +40");
            } else if (artistLower.contains(queryLower)) {
                score += 30; // 歌手包含查询
                logger.info("匹配: 歌手包含查询 +30");
            } else if (artistSimplified.equals(queryLower) || artistLower.equals(querySimplified.toLowerCase())) {
                score += 45; // 歌手繁简体精确匹配
                logger.info("匹配: 繁简体歌手精确匹配 +45");
            } else if (artistSimplified.startsWith(queryLower) || artistLower.startsWith(querySimplified.toLowerCase())) {
                score += 35; // 歌手繁简体前缀匹配
                logger.info("匹配: 繁简体歌手前缀匹配 +35");
            } else if (artistSimplified.contains(queryLower) || artistLower.contains(querySimplified.toLowerCase())) {
                score += 25; // 歌手繁简体包含匹配
                logger.info("匹配: 繁简体歌手包含匹配 +25");
            }
        }

        // 检查专辑
        String album = music.getAlbum();
        if (album != null && !album.isEmpty()) {
            String albumLower = album.toLowerCase();
            String albumSimplified = com.neko.music.util.ChineseConverter.toSimplified(album).toLowerCase();

            logger.info("专辑检查: albumLower={}, albumSimplified={}", albumLower, albumSimplified);

            if (albumLower.equals(queryLower)) {
                score += 20; // 专辑精确匹配
                logger.info("匹配: 专辑精确匹配 +20");
            } else if (albumLower.startsWith(queryLower)) {
                score += 15; // 专辑以查询开头
                logger.info("匹配: 专辑以查询开头 +15");
            } else if (albumLower.contains(queryLower)) {
                score += 10; // 专辑包含查询
                logger.info("匹配: 专辑包含查询 +10");
            } else if (albumSimplified.equals(queryLower) || albumLower.equals(querySimplified.toLowerCase())) {
                score += 18; // 专辑繁简体精确匹配
                logger.info("匹配: 繁简体专辑精确匹配 +18");
            } else if (albumSimplified.startsWith(queryLower) || albumLower.startsWith(querySimplified.toLowerCase())) {
                score += 13; // 专辑繁简体前缀匹配
                logger.info("匹配: 繁简体专辑前缀匹配 +13");
            } else if (albumSimplified.contains(queryLower) || albumLower.contains(querySimplified.toLowerCase())) {
                score += 8; // 专辑繁简体包含匹配
                logger.info("匹配: 繁简体专辑包含匹配 +8");
            }
        }

        // 如果以上都没有匹配，检查拼音匹配
        if (score == 0) {
            logger.info("尝试拼音匹配");
            if (matchMixedInput(music, query)) {
                score += 90; // 拼音匹配
                logger.info("匹配: 拼音匹配 +90");
            }
        }

        logger.info("最终分数: score={}", score);

        return score;
    }
    
    /**
     * 检查音乐是否匹配混合输入（拼音+中文）
     */
    private boolean matchMixedInput(Music music, String query) {
        // 检查标题
        if (matchFieldMixedInput(music.getTitle(), query)) {
            return true;
        }
        // 检查歌手
        if (matchFieldMixedInput(music.getArtist(), query)) {
            return true;
        }
        // 检查专辑
        if (matchFieldMixedInput(music.getAlbum(), query)) {
            return true;
        }
        return false;
    }
    
    /**
     * 检查单个字段是否匹配混合输入
     * 支持以下匹配方式：
     * 1. 纯拼音：hddjp 匹配 豪大大鸡排
     * 2. 完整拼音：haodadajipai 匹配 豪大大鸡排
     * 3. 混合输入：hao大大鸡排 匹配 豪大大鸡排
     * 4. 中文匹配：豪大大鸡排 匹配 豪大大鸡排
     */
    private boolean matchFieldMixedInput(String field, String query) {
        if (field == null || field.isEmpty()) {
            return false;
        }
        
        String queryLower = query.toLowerCase();
        
        // 首先检查是否是混合输入（同时包含拼音和中文）
        String pinyinPart = extractPinyinPart(query);
        String chinesePart = extractChinesePart(query);
        boolean isMixedInput = !pinyinPart.isEmpty() && !chinesePart.isEmpty();
        
        if (isMixedInput) {
            // 处理混合输入（如 hao大大鸡排）
            String fieldPinyin = com.neko.music.util.PinyinUtil.getPinyin(field);
            String fieldInitials = com.neko.music.util.PinyinUtil.getPinyinInitials(field);
            
            String pinyinPartLower = pinyinPart.toLowerCase();
            // logger.info("混合输入检查: field={}, query={}, pinyinPart={}, chinesePart={}, fieldPinyin={}, fieldInitials={}", 
            //     field, query, pinyinPart, chinesePart, fieldPinyin, fieldInitials);
            
            // 检查拼音部分是否匹配（宽松匹配：可以是前缀、包含等）
            boolean pinyinMatch = fieldPinyin.contains(pinyinPartLower) || 
                                  fieldInitials.contains(pinyinPartLower) ||
                                  fieldPinyin.startsWith(pinyinPartLower) ||
                                  fieldInitials.startsWith(pinyinPartLower);
            
            // 检查中文部分是否匹配（宽松匹配：可以是子串）
            boolean chineseMatch = field.contains(chinesePart);
            
            // logger.info("混合输入匹配结果: pinyinMatch={}, chineseMatch={}, field.contains(chinesePart)={}", 
            //     pinyinMatch, chineseMatch, field.contains(chinesePart));
            
            if (pinyinMatch && chineseMatch) {
                // logger.info("混合输入匹配成功(严格): field={}, query={}", field, query);
                return true;
            }
            
            // 尝试另一种匹配方式：只要拼音匹配或中文匹配即可
            // 例如：hao大大鸡排 可能只想匹配拼音 hao 开头的，或者包含 大大鸡排 的
            if (pinyinMatch || chineseMatch) {
                // logger.info("混合输入匹配成功(宽松): field={}, query={}", field, query);
                return true;
            }
        }
        
        // 1. 直接匹配（中文或英文）
        if (field.toLowerCase().contains(queryLower)) {
            // logger.info("直接匹配成功: field={}, query={}", field, query);
            return true;
        }
        
        // 2. 获取字段的拼音变体
        java.util.Set<String> variants = com.neko.music.util.PinyinUtil.getPinyinVariants(field);
        
        // 3. 检查查询字符串是否匹配任何拼音变体
        for (String variant : variants) {
            if (variant.contains(queryLower)) {
                // logger.info("拼音变体匹配成功: field={}, query={}, variant={}", field, query, variant);
                return true;
            }
        }
        
        // 4. 检查拼音首字母匹配（如 hddjp）
        String fieldInitials = com.neko.music.util.PinyinUtil.getPinyinInitials(field);
        if (fieldInitials.contains(queryLower)) {
            // logger.info("拼音首字母匹配成功: field={}, query={}, initials={}", field, query, fieldInitials);
            return true;
        }
        
        // 5. 检查完整拼音匹配
        String fieldPinyin = com.neko.music.util.PinyinUtil.getPinyin(field);
        if (fieldPinyin.contains(queryLower)) {
            // logger.info("完整拼音匹配成功: field={}, query={}, pinyin={}", field, query, fieldPinyin);
            return true;
        }
        
        return false;
    }
    
    /**
     * 从混合字符串中提取拼音部分
     */
    private String extractPinyinPart(String str) {
        StringBuilder pinyinPart = new StringBuilder();
        for (char c : str.toCharArray()) {
            if (Character.isLetter(c)) {
                pinyinPart.append(c);
            }
        }
        return pinyinPart.toString();
    }
    
    /**
     * 从混合字符串中提取中文部分
     */
    private String extractChinesePart(String str) {
        StringBuilder chinesePart = new StringBuilder();
        for (char c : str.toCharArray()) {
            if (isChinese(c)) {
                chinesePart.append(c);
            }
        }
        return chinesePart.toString();
    }
    
    /**
     * 判断字符是否是中文字符
     */
    private boolean isChinese(char c) {
        return (c >= 0x4E00 && c <= 0x9FA5) || 
               (c >= 0x3400 && c <= 0x4DBF);
    }
    
    // 内部类用于表示搜索请求
    private static class SearchRequest {
        private String query;
        
        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
    }
    
    // 内部类用于表示音乐对象
    private static class Music {
        private int id;
        private String title;
        private String artist;
        private String album;
        private int duration; // 时长，单位秒
        private String filePath;
        private String coverFilePath; // 封面路径
        private int uploadUserId;
        private String createdAt;
        
        // Getters and Setters
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
        
        // 获取封面URL，如果封面文件不存在则返回默认图标路径
        public String getCoverUrl() {
            if (coverFilePath == null || coverFilePath.isEmpty()) {
                return "/api/defaultIcon";
            }
            
            // 检查封面文件是否存在
            java.nio.file.Path coverPath = java.nio.file.Paths.get(coverFilePath);
            if (java.nio.file.Files.exists(coverPath)) {
                return coverFilePath;
            } else {
                // 如果文件不存在，返回默认图标路径
                return "/api/defaultIcon";
            }
        }
    }
    
    // 内部类用于表示搜索响应
    private static class SearchResponse {
        private boolean success;
        private String message;
        private Object results;
        
        public SearchResponse(boolean success, String message, Object results) {
            this.success = success;
            this.message = message;
            this.results = results;
        }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public Object getResults() { return results; }
        public void setResults(Object results) { this.results = results; }
    }
    
    // 内部类用于表示错误响应
    private static class ErrorResponse {
        private String error;
        
        public ErrorResponse(String error) {
            this.error = error;
        }
        
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }
}