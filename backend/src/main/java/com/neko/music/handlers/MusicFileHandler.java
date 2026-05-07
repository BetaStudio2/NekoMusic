package com.neko.music.handlers;

import com.neko.music.Main;
import com.neko.music.util.HttpResourceCache;
import com.neko.music.util.MusicAssetLocator;
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
import java.util.Optional;

public class MusicFileHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(MusicFileHandler.class);

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

        if (!musicRowExists(musicId)) {
            response.setStatus(HttpStatus.NOT_FOUND_404);
            response.setContentType("text/plain;charset=utf-8");
            response.getWriter().println("音乐文件不存在");
            return;
        }

        Optional<Path> audioOpt = MusicAssetLocator.findAudioFile(musicId);
        if (audioOpt.isEmpty()) {
            response.setStatus(HttpStatus.NOT_FOUND_404);
            response.setContentType("text/plain;charset=utf-8");
            response.getWriter().println("音乐文件不存在");
            return;
        }

        Path musicFile = audioOpt.get();
        if (!MusicAssetLocator.isUnderDirectory(musicFile, MusicAssetLocator.audioDir())) {
            logger.warn("拒绝提供音乐文件（路径不在允许目录内）: {}", musicFile);
            response.setStatus(HttpStatus.NOT_FOUND_404);
            response.setContentType("text/plain;charset=utf-8");
            response.getWriter().println("音乐文件不存在");
            return;
        }

        if (Files.exists(musicFile)) {
            sendMusicFile(musicFile, request, response);
            return;
        }

        response.setStatus(HttpStatus.NOT_FOUND_404);
        response.setContentType("text/plain;charset=utf-8");
        response.getWriter().println("音乐文件不存在");
    }

    private boolean musicRowExists(int musicId) {
        try (Connection conn = Main.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM music WHERE id = ? LIMIT 1")) {
            stmt.setInt(1, musicId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            logger.error("校验音乐记录时出错，音乐ID: {}", musicId, e);
            return false;
        }
    }
    
    private void sendMusicFile(Path musicPath, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String fileName = musicPath.getFileName().toString().toLowerCase();
        String contentType = getContentTypeByExtension(fileName);
        long fileSize = Files.size(musicPath);
        String etag = HttpResourceCache.strongEtagForFile(musicPath);
        String rangeHeader = request.getHeader("Range");

        if (rangeHeader == null || !rangeHeader.startsWith("bytes=")) {
            if (HttpResourceCache.sendNotModifiedIfFresh(request, response, etag)) {
                HttpResourceCache.setAcceptRangesBytes(response);
                return;
            }
        }

        response.setContentType(contentType);
        HttpResourceCache.setAcceptRangesBytes(response);

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            HttpResourceCache.applyFileCachingHeaders(musicPath, response);
            handleRangeRequest(musicPath, response, rangeHeader, fileSize, contentType);
        } else {
            response.setStatus(HttpStatus.OK_200);
            HttpResourceCache.applyFileCachingHeaders(musicPath, response);
            response.setContentLengthLong(fileSize);

            try (InputStream inputStream = Files.newInputStream(musicPath);
                 OutputStream outputStream = response.getOutputStream()) {

                byte[] buffer = new byte[65536];
                int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                outputStream.flush();
            }
        }
    }

    private String getContentTypeByExtension(String fileName) {
        if (fileName.endsWith(".mp3")) {
            return "audio/mpeg";
        } else if (fileName.endsWith(".flac")) {
            return "audio/flac";
        } else if (fileName.endsWith(".wav")) {
            return "audio/wav";
        }
        return "audio/mpeg";
    }
    
    private void handleRangeRequest(Path musicPath, HttpServletResponse response,
                                    String rangeHeader, long fileSize, String contentType) throws IOException {
        String rangeValue = rangeHeader.replace("bytes=", "");

        String[] ranges = rangeValue.split("-");
        long start = Long.parseLong(ranges[0]);
        long end = ranges.length > 1 && !ranges[1].isEmpty() ? Long.parseLong(ranges[1]) : fileSize - 1;

        end = Math.min(end, fileSize - 1);

        long contentLength = end - start + 1;

        response.setStatus(HttpStatus.PARTIAL_CONTENT_206);
        response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileSize);
        response.setContentLengthLong(contentLength);
        response.setContentType(contentType);

        try (RandomAccessFile randomAccessFile = new RandomAccessFile(musicPath.toFile(), "r");
             OutputStream outputStream = response.getOutputStream()) {

            randomAccessFile.seek(start);

            byte[] buffer = new byte[65536];
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
