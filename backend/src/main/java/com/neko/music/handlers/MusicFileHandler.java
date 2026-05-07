package com.neko.music.handlers;

import com.neko.music.Main;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class MusicFileHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(MusicFileHandler.class);

    /** 热点曲目路径缓存，减轻反复 SELECT file_path（LRU 约 2048 条） */
    private static final Map<Integer, String> MUSIC_FILE_PATH_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(512, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, String> eldest) {
                    return size() > 2048;
                }
            });

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        
        if (pathInfo == null || pathInfo.equals("/")) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.setContentType("text/plain;charset=utf-8");
            response.getWriter().println("音乐ID不能为空");
            return;
        }
        
        // 解析音乐ID (路径格式: /{id})
        String idStr = pathInfo.replace("/", "");
        int musicId;
        
        try {
            musicId = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.setContentType("text/plain;charset=utf-8");
            response.getWriter().println("无效的音乐ID");
            return;
        }
        
        // 根据音乐ID查找对应的文件路径
        String musicFilePath = getMusicFilePathById(musicId);
        
        if (musicFilePath != null && !musicFilePath.isEmpty()) {
            // 检查音乐文件是否存在
            Path musicFile = Paths.get(musicFilePath);
            if (Files.exists(musicFile)) {
                // 文件存在，发送音乐文件
                sendMusicFile(musicFile, request, response);
                return;
            }
        }
        
        // 如果音乐文件不存在或为空，返回404
        response.setStatus(HttpStatus.NOT_FOUND_404);
        response.setContentType("text/plain;charset=utf-8");
        response.getWriter().println("音乐文件不存在");
    }
    
    /**
     * 根据音乐ID获取文件路径
     */
    private String getMusicFilePathById(int musicId) {
        String cached = MUSIC_FILE_PATH_CACHE.get(musicId);
        if (cached != null) {
            return cached;
        }

        String filePath = null;
        
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            String sql = "SELECT file_path FROM music WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, musicId);
                
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    filePath = rs.getString("file_path");
                }
            }
        } catch (Exception e) {
            logger.error("查询音乐文件路径时出错，音乐ID: " + musicId, e);
        }

        if (filePath != null && !filePath.isEmpty()) {
            MUSIC_FILE_PATH_CACHE.put(musicId, filePath);
        }

        return filePath;
    }
    
    /**
     * 发送音乐文件
     */
    private void sendMusicFile(Path musicPath, HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 根据文件扩展名设置正确的 MIME 类型
        String fileName = musicPath.getFileName().toString().toLowerCase();
        String contentType = getContentTypeByExtension(fileName);
        response.setContentType(contentType);
        response.setStatus(HttpStatus.OK_200);

        // 设置Content-Length头
        long fileSize = Files.size(musicPath);
        response.setContentLengthLong(fileSize);

        // 支持范围请求（用于音频播放器的跳转功能）
        String rangeHeader = request.getHeader("Range");
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            // 处理范围请求
            handleRangeRequest(musicPath, response, rangeHeader, fileSize, contentType);
        } else {
            // 发送完整文件 - 使用大buffer减少系统调用
            try (InputStream inputStream = Files.newInputStream(musicPath);
                 OutputStream outputStream = response.getOutputStream()) {

                byte[] buffer = new byte[65536]; // 64KB buffer
                int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                outputStream.flush();
            }
        }
    }

    /**
     * 根据文件扩展名获取 MIME 类型
     */
    private String getContentTypeByExtension(String fileName) {
        if (fileName.endsWith(".mp3")) {
            return "audio/mpeg";
        } else if (fileName.endsWith(".flac")) {
            return "audio/flac";
        } else if (fileName.endsWith(".wav")) {
            return "audio/wav";
        }
        // 默认返回 MP3 类型
        return "audio/mpeg";
    }
    
    /**
     * 处理范围请求（支持音频播放器的拖拽功能）
     */
    private void handleRangeRequest(Path musicPath, HttpServletResponse response, String rangeHeader, long fileSize, String contentType) throws IOException {
        String rangeValue = rangeHeader.replace("bytes=", "");

        String[] ranges = rangeValue.split("-");
        long start = Long.parseLong(ranges[0]);
        long end = ranges.length > 1 && !ranges[1].isEmpty() ? Long.parseLong(ranges[1]) : fileSize - 1;

        // 确保end不超过文件大小
        end = Math.min(end, fileSize - 1);

        long contentLength = end - start + 1;

        response.setStatus(HttpStatus.PARTIAL_CONTENT_206);
        response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileSize);
        response.setContentLengthLong(contentLength);
        response.setContentType(contentType);

        try (RandomAccessFile randomAccessFile = new RandomAccessFile(musicPath.toFile(), "r");
             OutputStream outputStream = response.getOutputStream()) {

            // 跳转到起始位置
            randomAccessFile.seek(start);

            byte[] buffer = new byte[65536]; // 64KB buffer
            long bytesToRead = contentLength;

            while (bytesToRead > 0) {
                int bytesRead = randomAccessFile.read(buffer, 0, (int) Math.min(buffer.length, bytesToRead));
                if (bytesRead == -1) break;

                outputStream.write(buffer, 0, bytesRead);
                bytesToRead -= bytesRead;
            }

            outputStream.flush();
        }
    }
}