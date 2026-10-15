package com.neko.music.handlers;

import com.neko.music.model.SuccessResponse;
import com.neko.music.model.ErrorResponse;
import com.neko.music.Main;
import com.neko.music.service.EmbeddedMetadataSyncService;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.OptionalInt;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import com.neko.music.i18n.ApiMessages;

public class MusicLyricsHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(MusicLyricsHandler.class);

    /** LRC 时间戳补毫秒（{@code [00:12:34]} → {@code [00:12.34]}）。预编译，避免每条请求重新编译正则。 */
    private static final Pattern LRC_TIMESTAMP = Pattern.compile("\\[(\\d{2}):(\\d{2}):(\\d{2,3})\\]");

    /**
     * 播放计数与防刷在响应之后执行：它不参与响应内容，留在请求线程里则每次「新 IP + 新曲目」
     * 都要多付一次 Redis 往返和一次 {@code UPDATE music} 提交（毫秒级）。
     * 队列满即丢弃——播放计数是尽力而为的统计，不值得让请求排队。
     */
    private static final ExecutorService PLAY_COUNT_EXECUTOR = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1024),
            Thread.ofPlatform().daemon().name("lyrics-play-count-", 0).factory(),
            new ThreadPoolExecutor.DiscardPolicy());

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        OptionalInt musicIdOpt = parseMusicId(request, response);
        if (musicIdOpt.isEmpty()) {
            return;
        }
        int musicId = musicIdOpt.getAsInt();

        // 获取歌词信息
        String lyrics = getLyricsById(musicId);

        if (lyrics == null) {
            writeError(response, HttpStatus.NOT_FOUND_404, "music.lyrics.file_missing");
            return;
        }

        // 增加播放次数（带防刷逻辑）：IP 必须在请求线程里取好，异步阶段 request 可能已被容器回收
        submitPlayCount(musicId, getClientIpAddress(request));

        byte[] body = Main.getObjectMapper()
                .writeValueAsBytes(new LyricsResponse(true, ApiMessages.translate("music.lyrics.get_ok"), lyrics));
        response.setStatus(HttpStatus.OK_200);
        response.setContentType("application/json;charset=utf-8");
        response.getOutputStream().write(body);
        response.getOutputStream().flush();
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 检查是否为管理员
        if (!isAdminRequest(request)) {
            writeError(response, HttpStatus.UNAUTHORIZED_401, "admin.permission_required");
            return;
        }
        
        OptionalInt musicIdOpt = parseMusicId(request, response);
        if (musicIdOpt.isEmpty()) {
            return;
        }
        int musicId = musicIdOpt.getAsInt();
        
        // 读取请求体中的歌词
        StringBuilder requestBody = new StringBuilder();
        String line;
        while ((line = request.getReader().readLine()) != null) {
            requestBody.append(line);
        }
        
        LyricsRequest lyricsRequest;
        try {
            lyricsRequest = Main.getObjectMapper().readValue(requestBody.toString(), LyricsRequest.class);
        } catch (Exception e) {
            writeError(response, HttpStatus.BAD_REQUEST_400, "user.bad_request");
            return;
        }
        
        if (lyricsRequest.lyrics() == null) {
            writeError(response, HttpStatus.BAD_REQUEST_400, "music.lyrics.content_required");
            return;
        }
        
        // 更新数据库歌词
        boolean success = updateLyrics(musicId, lyricsRequest.lyrics());
        
        if (!success) {
            writeError(response, HttpStatus.INTERNAL_SERVER_ERROR_500, "music.lyrics.update_failed");
            return;
        }

        if (Main.getLyricsSearchIndex() != null) {
            Main.getLyricsSearchIndex().rebuildOne(musicId);
        }

        // 同步内嵌歌词到音频文件（保留广告元数据与横幅）
        EmbeddedMetadataSyncService.syncOne(musicId);
        
        writeSuccess(response, ApiMessages.translate("music.lyrics.update_ok"));
    }
    
    /**
     * 解析路径中的音乐ID；缺失或非法时写出错误响应并返回空。
     */
    private OptionalInt parseMusicId(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String pathInfo = request.getPathInfo();
        
        if (pathInfo == null || pathInfo.equals("/")) {
            writeError(response, HttpStatus.BAD_REQUEST_400, "music.id_required");
            return OptionalInt.empty();
        }
        
        // 解析音乐ID (路径格式: /{id})
        String idStr = pathInfo.replace("/", "");
        
        try {
            return OptionalInt.of(Integer.parseInt(idStr));
        } catch (NumberFormatException e) {
            writeError(response, HttpStatus.BAD_REQUEST_400, "music.id_invalid");
            return OptionalInt.empty();
        }
    }
    
    /**
     * 写出 {@code {"error":"..."}} 错误响应（保持原有格式与换行行为）。
     */
    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=utf-8");
        ErrorResponse errorResponse = new ErrorResponse(message);
        response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
    }
    
    /**
     * 写出 {@code {"success":true,"message":"..."}} 成功响应（HTTP 200）。
     */
    private void writeSuccess(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.OK_200);
        response.setContentType("application/json;charset=utf-8");
        SuccessResponse successResponse = new SuccessResponse(true, message);
        response.getWriter().println(Main.getObjectMapper().writeValueAsString(successResponse));
    }
    
    /**
     * 根据音乐ID获取歌词
     */
    private String getLyricsById(int musicId) {
        var stored = Main.getLyricsDatabaseManager().findByMusicIdCached(musicId);
        if (stored.isPresent()) {
            return normalizeLrc(stored.get().content());
        }
        logger.debug("数据库歌词不存在 musicId={}", musicId);
        return null;
    }
    
    /**
     * 更新数据库歌词
     */
    private boolean updateLyrics(int musicId, String lyrics) {
        boolean ok = Main.getLyricsDatabaseManager().upsert(musicId, lyrics, "admin");
        if (ok) {
            logger.info("成功更新数据库歌词 musicId={}", musicId);
        }
        return ok;
    }

    private String normalizeLrc(String lyricsContent) {
        if (lyricsContent == null) {
            return null;
        }
        return LRC_TIMESTAMP.matcher(lyricsContent).replaceAll("[$1:$2.$3]");
    }

    /** 把播放计数丢到后台线程；客户端 IP 已在请求线程里解析完成。 */
    private void submitPlayCount(int musicId, String ipAddress) {
        PLAY_COUNT_EXECUTOR.execute(() -> incrementPlayCount(musicId, ipAddress));
    }

    /**
     * 增加音乐的播放次数（带防刷逻辑）
     */
    private void incrementPlayCount(int musicId, String ipAddress) {
        try {
            if (ipAddress == null || ipAddress.isEmpty()) {
                logger.warn("无法获取客户端IP地址，跳过播放次数统计");
                return;
            }
            
            // 生成Redis键名：play_log:musicId:ipAddress
            String redisKey = "play_log:" + musicId + ":" + ipAddress;
            
            // 检查Redis中是否已存在该键（一分钟内已播放过）
            boolean exists = Main.getRedisService().exists(redisKey);
            
            if (exists) {
                logger.debug("同一IP {} 在一分钟内已播放过音乐ID {}，跳过播放次数统计", ipAddress, musicId);
                return; // 一分钟内已播放过，不计入播放次数
            }
            
            // 增加播放次数
            try (java.sql.Connection conn = Main.getDatabaseManager().getConnection();
                 java.sql.PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE music SET play_count = play_count + 1 WHERE id = ?")) {
                stmt.setInt(1, musicId);
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected > 0) {
                    logger.info("已增加音乐ID {} 的播放次数 (IP: {})", musicId, ipAddress);
                } else {
                    logger.warn("未找到音乐ID {}，无法更新播放次数", musicId);
                }
            }
            
            // 在Redis中记录播放日志，设置60秒过期时间
            Main.getRedisService().setWithExpiry(redisKey, "1", 60);
            
        } catch (Exception e) {
            logger.error("增加播放次数时出错: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 获取客户端IP地址（统一走 {@link com.neko.music.util.ClientIpResolver}，避免信任可伪造的转发头）。
     */
    private String getClientIpAddress(HttpServletRequest request) {
        return com.neko.music.util.ClientIpResolver.clientIp(request);
    }
    
    /**
     * 检查请求是否来自管理员
     */
    private boolean isAdminRequest(HttpServletRequest request) {
        String adminToken = request.getHeader("Admin-Token");
        if (adminToken == null || adminToken.isEmpty()) {
            return false;
        }
        
        // 验证管理员令牌
        return Main.getAdminAuthService().validateAdminToken(adminToken);
    }

    // 内部类用于表示歌词请求
    // 歌词请求（Jackson 反序列化，只读）
    private record LyricsRequest(String lyrics) {
    }
    
    // 内部类用于表示歌词响应
    private record LyricsResponse(boolean success, String message, String data) {
    }
    
    // 内部类用于表示成功响应
    
    // 内部类用于表示错误响应
}
