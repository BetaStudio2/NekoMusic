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
            String queryLower = query.toLowerCase();

            if (isPinyin) {
                // 拼音搜索：利用预计算拼音列在SQL层筛选，避免全表加载
                StringBuilder sqlBuilder = new StringBuilder();
                sqlBuilder.append("SELECT artist, COUNT(*) as music_count FROM music ");
                sqlBuilder.append("WHERE (artist LIKE ? OR artist_pinyin LIKE ? OR artist_pinyin_initials LIKE ? OR artist_word_initials LIKE ?) ");
                sqlBuilder.append("GROUP BY artist ORDER BY music_count DESC LIMIT 1");

                try (Connection conn = databaseManager.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sqlBuilder.toString())) {
                    String likeQuery = "%" + queryLower + "%";
                    stmt.setString(1, likeQuery);
                    stmt.setString(2, likeQuery);
                    stmt.setString(3, likeQuery);
                    stmt.setString(4, likeQuery);

                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        foundArtist = rs.getString("artist");
                        musicCount = rs.getInt("music_count");
                    }
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