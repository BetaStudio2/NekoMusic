package com.neko.music.handlers;

import com.google.gson.Gson;
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
    private final Gson gson = new Gson();
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

            JsonObject requestData = gson.fromJson(requestBody.toString(), JsonObject.class);

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
            List<JsonObject> artists = searchArtists(query);

            logger.info("搜索到 {} 个歌手: query={}", artists.size(), query);

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("message", "搜索成功");
            response.addProperty("total", artists.size());

            JsonArray artistsArray = new JsonArray();
            for (JsonObject artist : artists) {
                artistsArray.add(artist);
            }

            response.add("results", artistsArray);

            sendSuccessResponse(resp, response);
        } catch (Exception e) {
            logger.error("处理搜索歌手请求时发生错误: {}", e.getMessage(), e);
            sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "服务器内部错误");
        }
    }

    /**
     * 搜索歌手
     */
    private List<JsonObject> searchArtists(String query) {
        List<JsonObject> artists = new ArrayList<>();
        
        // SQL 查询：从 music 表中搜索 artist 字段，并统计每个歌手的音乐数量
        String sql = "SELECT artist, COUNT(*) as music_count, " +
                     "(SELECT cover_path FROM music m2 WHERE m2.artist = m1.artist LIMIT 1) as cover_path " +
                     "FROM music m1 " +
                     "WHERE artist LIKE ? " +
                     "GROUP BY artist " +
                     "ORDER BY music_count DESC";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, "%" + query + "%");
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                JsonObject artist = new JsonObject();
                artist.addProperty("name", rs.getString("artist"));
                artist.addProperty("musicCount", rs.getInt("music_count"));
                artist.addProperty("coverPath", rs.getString("cover_path"));
                artists.add(artist);
            }

            logger.info("搜索歌手成功: query={}, count={}", query, artists.size());
        } catch (Exception e) {
            logger.error("搜索歌手失败: {}", e.getMessage(), e);
        }

        return artists;
    }

    /**
     * 发送成功响应
     */
    private void sendSuccessResponse(HttpServletResponse resp, JsonObject response) throws IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
        try (PrintWriter out = resp.getWriter()) {
            out.print(gson.toJson(response));
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
            out.print(gson.toJson(response));
            out.flush();
        }
    }
}