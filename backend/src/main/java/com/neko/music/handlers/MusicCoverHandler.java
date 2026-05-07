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

public class MusicCoverHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(MusicCoverHandler.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        
        if (pathInfo == null || pathInfo.equals("/")) {
            sendDefaultIcon(request, response);
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
            response.getWriter().println("Invalid music ID");
            return;
        }

        if (!musicRowExists(musicId)) {
            sendDefaultIcon(request, response);
            return;
        }

        Optional<Path> coverOpt = MusicAssetLocator.findCoverFile(musicId);
        if (coverOpt.isPresent()) {
            Path coverFile = coverOpt.get();
            if (MusicAssetLocator.isUnderDirectory(coverFile, MusicAssetLocator.coverDir()) && Files.exists(coverFile)) {
                sendImageFile(request, coverFile, response);
                return;
            }
        }

        sendDefaultIcon(request, response);
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
    
    private void sendImageFile(HttpServletRequest request, Path imagePath, HttpServletResponse response) throws IOException {
        String etag = HttpResourceCache.strongEtagForFile(imagePath);
        if (HttpResourceCache.sendNotModifiedIfFresh(request, response, etag)) {
            return;
        }

        String mimeType = getMimeType(imagePath.toString());
        response.setContentType(mimeType);
        response.setStatus(HttpStatus.OK_200);
        HttpResourceCache.applyFileCachingHeaders(imagePath, response);
        
        try (InputStream inputStream = Files.newInputStream(imagePath);
             OutputStream outputStream = response.getOutputStream()) {
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            
            outputStream.flush();
        }
    }
    
    private void sendDefaultIcon(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (HttpResourceCache.sendNotModifiedDefaultIcon(request, response)) {
            return;
        }

        try {
            InputStream defaultIconStream = getClass().getClassLoader().getResourceAsStream("DefaultIcon.png");
            if (defaultIconStream != null) {
                response.setContentType("image/png");
                response.setStatus(HttpStatus.OK_200);
                HttpResourceCache.applyDefaultIconCachingHeaders(response);
                
                try (InputStream inputStream = defaultIconStream;
                     OutputStream outputStream = response.getOutputStream()) {
                    
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    
                    outputStream.flush();
                }
            } else {
                response.setStatus(HttpStatus.NOT_FOUND_404);
                response.setContentType("text/plain;charset=utf-8");
                response.getWriter().println("Default icon not found");
            }
        } catch (Exception e) {
            logger.error("发送默认图标时出错", e);
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
            response.setContentType("text/plain;charset=utf-8");
            response.getWriter().println("Error loading default icon");
        }
    }
    
    private String getMimeType(String filePath) {
        String extension = getFileExtension(filePath).toLowerCase();
        
        switch (extension) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "bmp":
                return "image/bmp";
            case "webp":
                return "image/webp";
            default:
                return "image/jpeg";
        }
    }
    
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return fileName.substring(lastDotIndex + 1);
        }
        return "";
    }
}
