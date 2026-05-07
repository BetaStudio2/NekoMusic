package com.neko.music.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.neko.music.Main;
import com.neko.music.database.DatabaseManager;
import com.neko.music.util.MusicAssetLocator;
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
     * 搜索歌手（单连接完成聚合查询 + 曲目列表，配合 idx_music_artist 降低第二段查询成本）
     */
    private JsonObject searchArtists(String query) {
        logger.debug("searchArtists: query len={}", query != null ? query.length() : 0);

        JsonObject result = new JsonObject();
        boolean isPinyin = com.neko.music.util.PinyinUtil.isLikelyPinyin(query);

        String foundArtist = null;
        int musicCount = 0;
        List<JsonObject> musicList = new ArrayList<>();

        String musicSql = """
            SELECT id, title, artist, album, duration, file_format, language
            FROM music
            WHERE artist = ?
            ORDER BY id
            """;

        try (Connection conn = databaseManager.getConnection()) {
            String queryLower = query.toLowerCase();

            if (isPinyin) {
                String aggSql = """
                    SELECT artist, COUNT(*) AS music_count FROM music
                    WHERE (artist LIKE ? OR artist_pinyin LIKE ? OR artist_pinyin_initials LIKE ? OR artist_word_initials LIKE ?)
                    GROUP BY artist ORDER BY music_count DESC LIMIT 1
                    """;
                try (PreparedStatement stmt = conn.prepareStatement(aggSql)) {
                    String likeQuery = "%" + queryLower + "%";
                    stmt.setString(1, likeQuery);
                    stmt.setString(2, likeQuery);
                    stmt.setString(3, likeQuery);
                    stmt.setString(4, likeQuery);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            foundArtist = rs.getString("artist");
                            musicCount = rs.getInt("music_count");
                        }
                    }
                }
            } else {
                List<String> variants = com.neko.music.util.ChineseConverter.getFullSearchVariants(query);
                logger.debug("searchArtists variants: {}", variants);

                StringBuilder sqlBuilder = new StringBuilder();
                sqlBuilder.append("SELECT artist, COUNT(*) AS music_count FROM music WHERE (");
                List<String> conditions = new ArrayList<>();
                for (int i = 0; i < variants.size(); i++) {
                    conditions.add("artist LIKE ?");
                }
                sqlBuilder.append(String.join(" OR ", conditions));
                sqlBuilder.append(") GROUP BY artist ORDER BY music_count DESC LIMIT 1");

                try (PreparedStatement stmt = conn.prepareStatement(sqlBuilder.toString())) {
                    int paramIndex = 1;
                    for (String variant : variants) {
                        stmt.setString(paramIndex++, "%" + variant + "%");
                    }
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            foundArtist = rs.getString("artist");
                            musicCount = rs.getInt("music_count");
                        }
                    }
                }
            }

            if (foundArtist == null) {
                result.addProperty("name", "");
                result.addProperty("musicCount", 0);
                result.add("musicList", new JsonArray());
                return result;
            }

            try (PreparedStatement stmt = conn.prepareStatement(musicSql)) {
                stmt.setString(1, foundArtist);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        JsonObject music = new JsonObject();
                        music.addProperty("id", rs.getInt("id"));
                        music.addProperty("title", rs.getString("title"));
                        music.addProperty("artist", rs.getString("artist"));
                        music.addProperty("album", rs.getString("album"));
                        music.addProperty("duration", rs.getInt("duration"));
                        int mid = rs.getInt("id");
                        music.addProperty("coverPath", MusicAssetLocator.coverApiUrl(mid));
                        music.addProperty("filePath", MusicAssetLocator.fileApiUrl(mid));
                        music.addProperty("fileFormat", rs.getString("file_format"));
                        music.addProperty("language", rs.getString("language"));
                        musicList.add(music);
                    }
                }
            }

            logger.info("搜索歌手成功: query={}, artist={}, tracks={}", query, foundArtist, musicList.size());
        } catch (Exception e) {
            logger.error("搜索歌手失败: {}", e.getMessage(), e);
            result.addProperty("name", "");
            result.addProperty("musicCount", 0);
            result.add("musicList", new JsonArray());
            return result;
        }

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