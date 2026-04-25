package com.neko.music.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.neko.music.Main;
import com.neko.music.database.DatabaseManager;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

@WebServlet("/api/artists/search")
public class SearchArtistsHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(SearchArtistsHandler.class);
    private DatabaseManager databaseManager;

    @Override
    public void init() throws ServletException {
        databaseManager = Main.getDatabaseManager();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        logger.info("收到搜索歌手请求");

        try {
            // 读取请求体
            StringBuilder requestBody = new StringBuilder();
            String line;
            while ((line = req.getReader().readLine()) != null) {
                requestBody.append(line);
            }

            JsonObject requestData = Main.getGson().fromJson(requestBody.toString(), JsonObject.class);

            if (requestData == null || !requestData.has("query")) {
                sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "缺少搜索关键词");
                return;
            }

            String query = requestData.get("query").getAsString().trim();

            if (query.isEmpty()) {
                sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST, "搜索关键词不能为空");
                return;
            }

            // 搜索歌手
            JsonObject artistResult = searchArtists(query);

            logger.info("搜索歌手结果: query={}", query);

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("message", "搜索成功");

            response.add("artist", artistResult);

            sendSuccessResponse(resp, response);
        } catch (Exception e) {
            logger.error("处理搜索歌手请求时发生错误: {}", e.getMessage(), e);
            sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "服务器内部错误");
        }
    }

    /**
     * 搜索歌手
     */
    private JsonObject searchArtists(String query) {
        logger.info("=== searchArtists 方法开始执行 ===");
        logger.info("查询字符串: query='{}', 长度={}", query, query != null ? query.length() : 0);
        
        JsonObject result = new JsonObject();
        
        // 判断查询是否是拼音
        boolean isPinyin = com.neko.music.util.PinyinUtil.isLikelyPinyin(query);
        logger.info("是否是拼音: isPinyin={}", isPinyin);
        
        String foundArtist = null;
        int musicCount = 0;

        try {
            if (isPinyin) {
                // 如果是拼音，查询所有歌手（在应用层面进行拼音匹配）
                String sql = "SELECT artist, COUNT(*) as music_count " +
                           "FROM music " +
                           "GROUP BY artist " +
                           "ORDER BY music_count DESC";
                
                logger.info("拼音搜索模式: 查询所有歌手");
                
                try (Connection conn = databaseManager.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    
                    ResultSet rs = stmt.executeQuery();
                    int artistCount = 0;
                    
                    while (rs.next()) {
                        artistCount++;
                        String artist = rs.getString("artist");
                        boolean matched = matchFieldMixedInput(artist, query);
                        logger.debug("检查歌手: artist={}, matched={}", artist, matched);
                        
                        if (matched) {
                            foundArtist = artist;
                            musicCount = rs.getInt("music_count");
                            logger.info("找到匹配歌手: artist={}, musicCount={}", foundArtist, musicCount);
                            break;
                        }
                    }
                    
                    logger.info("拼音搜索完成: 检查了 {} 个歌手", artistCount);
                }
            } else {
                // 如果不是拼音，使用正常的繁简体搜索
                List<String> variants = com.neko.music.util.ChineseConverter.getFullSearchVariants(query);
                logger.info("搜索变体: query={}, variants={}", query, variants);
                
                StringBuilder sqlBuilder = new StringBuilder();
                sqlBuilder.append("SELECT artist, COUNT(*) as music_count ");
                sqlBuilder.append("FROM music ");
                sqlBuilder.append("WHERE (");
                
                List<String> conditions = new ArrayList<>();
                for (int i = 0; i < variants.size(); i++) {
                    conditions.add("artist LIKE ?");
                }
                sqlBuilder.append(String.join(" OR ", conditions));
                sqlBuilder.append(") ");
                sqlBuilder.append("GROUP BY artist ");
                sqlBuilder.append("ORDER BY music_count DESC ");
                sqlBuilder.append("LIMIT 1");

                logger.info("SQL查询: {}", sqlBuilder.toString());

                try (Connection conn = databaseManager.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sqlBuilder.toString())) {

                    // 设置参数
                    int paramIndex = 1;
                    for (String variant : variants) {
                        String paramValue = "%" + variant + "%";
                        stmt.setString(paramIndex++, paramValue);
                        logger.info("参数 {}: {}", paramIndex - 1, paramValue);
                    }
                    
                    ResultSet rs = stmt.executeQuery();

                    if (rs.next()) {
                        foundArtist = rs.getString("artist");
                        musicCount = rs.getInt("music_count");
                    }
                }
            }

            logger.info("搜索歌手成功: query={}, artist={}", query, foundArtist);
        } catch (Exception e) {
            logger.error("搜索歌手失败: {}", e.getMessage(), e);
            // 返回空结果
            result.addProperty("name", "");
            result.addProperty("musicCount", 0);
            result.add("musicList", new JsonArray());
            return result;
        }

        // 如果没有找到歌手，返回空结果
        if (foundArtist == null) {
            result.addProperty("name", "");
            result.addProperty("musicCount", 0);
            result.add("musicList", new JsonArray());
            return result;
        }

        // 获取该歌手的所有音乐
        List<JsonObject> musicList = new ArrayList<>();
        String musicSql = "SELECT id, title, artist, album, duration, cover_path, file_path, file_format, language " +
                         "FROM music " +
                         "WHERE artist = ? " +
                         "ORDER BY id";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(musicSql)) {

            stmt.setString(1, foundArtist);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                JsonObject music = new JsonObject();
                music.addProperty("id", rs.getInt("id"));
                music.addProperty("title", rs.getString("title"));
                music.addProperty("artist", rs.getString("artist"));
                music.addProperty("album", rs.getString("album"));
                music.addProperty("duration", rs.getInt("duration"));
                music.addProperty("coverPath", rs.getString("cover_path"));
                music.addProperty("filePath", rs.getString("file_path"));
                music.addProperty("fileFormat", rs.getString("file_format"));
                music.addProperty("language", rs.getString("language"));
                musicList.add(music);
            }

            logger.info("获取歌手音乐成功: artist={}, count={}", foundArtist, musicList.size());
        } catch (Exception e) {
            logger.error("获取歌手音乐失败: {}", e.getMessage(), e);
        }

        // 构建返回结果
        result.addProperty("name", foundArtist);
        result.addProperty("musicCount", musicCount);
        
        JsonArray musicArray = new JsonArray();
        for (JsonObject music : musicList) {
            musicArray.add(music);
        }
        result.add("musicList", musicArray);

        return result;
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
            
            // 检查拼音部分是否匹配（宽松匹配：可以是前缀、包含等）
            boolean pinyinMatch = fieldPinyin.contains(pinyinPartLower) || 
                                  fieldInitials.contains(pinyinPartLower) ||
                                  fieldPinyin.startsWith(pinyinPartLower) ||
                                  fieldInitials.startsWith(pinyinPartLower);
            
            // 检查中文部分是否匹配（宽松匹配：可以是子串）
            boolean chineseMatch = field.contains(chinesePart);
            
            // 严格匹配：拼音和中文都要匹配
            if (pinyinMatch && chineseMatch) {
                return true;
            }
            
            // 宽松匹配：只要拼音匹配或中文匹配即可
            // 例如：hao大大鸡排 可能只想匹配拼音 hao 开头的，或者包含 大大鸡排 的
            if (pinyinMatch || chineseMatch) {
                return true;
            }
        }
        
        // 1. 直接匹配（中文或英文）
        if (field.toLowerCase().contains(queryLower)) {
            return true;
        }
        
        // 2. 获取字段的拼音变体
        java.util.Set<String> variants = com.neko.music.util.PinyinUtil.getPinyinVariants(field);
        
        // 3. 检查查询字符串是否匹配任何拼音变体
        for (String variant : variants) {
            if (variant.contains(queryLower)) {
                return true;
            }
        }
        
        // 4. 检查拼音首字母匹配（如 hddjp）
        String fieldInitials = com.neko.music.util.PinyinUtil.getPinyinInitials(field);
        if (fieldInitials.contains(queryLower)) {
            return true;
        }
        
        // 5. 检查完整拼音匹配
        String fieldPinyin = com.neko.music.util.PinyinUtil.getPinyin(field);
        if (fieldPinyin.contains(queryLower)) {
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

    /**
     * 发送成功响应
     */
    private void sendSuccessResponse(HttpServletResponse resp, JsonObject response) throws IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
        try (PrintWriter out = resp.getWriter()) {
            out.print(Main.getGson().toJson(response));
            out.flush();
        }
    }

    /**
     * 发送错误响应
     */
    private void sendErrorResponse(HttpServletResponse resp, int statusCode, String message) throws IOException {
        resp.setStatus(statusCode);
        JsonObject response = new JsonObject();
        response.addProperty("success", false);
        response.addProperty("message", message);

        try (PrintWriter out = resp.getWriter()) {
            out.print(Main.getGson().toJson(response));
            out.flush();
        }
    }
}