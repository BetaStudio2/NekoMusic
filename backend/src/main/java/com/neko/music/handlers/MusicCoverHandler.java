package com.neko.music.handlers;

import com.neko.music.Main;
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
            // 如果路径为空，返回默认图标
            sendDefaultIcon(response);
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
            sendDefaultIcon(response);
            return;
        }

        Optional<Path> coverOpt = MusicAssetLocator.findCoverFile(musicId);
        if (coverOpt.isPresent()) {
            Path coverFile = coverOpt.get();
            if (MusicAssetLocator.isUnderDirectory(coverFile, MusicAssetLocator.coverDir()) && Files.exists(coverFile)) {
                sendImageFile(coverFile, response);
                return;
            }
        }

        sendDefaultIcon(response);
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
    
    /**
     * 发送图片文件
     */
    private void sendImageFile(Path imagePath, HttpServletResponse response) throws IOException {
        String mimeType = getMimeType(imagePath.toString());
        response.setContentType(mimeType);
        response.setStatus(HttpStatus.OK_200);
        
        try (InputStream inputStream = Files.newInputStream(imagePath);
             OutputStream outputStream = response.getOutputStream()) {
            
            byte[] buffer = new byte[8192]; // 8KB buffer
            int bytesRead;
            
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            
            outputStream.flush();
        }
    }
    
    /**
     * 发送默认图标
     */
    private void sendDefaultIcon(HttpServletResponse response) throws IOException {
        try {
            // 尝试从类路径加载默认图标
            InputStream defaultIconStream = getClass().getClassLoader().getResourceAsStream("DefaultIcon.png");
            if (defaultIconStream != null) {
                response.setContentType("image/png");
                response.setStatus(HttpStatus.OK_200);
                
                try (InputStream inputStream = defaultIconStream;
                     OutputStream outputStream = response.getOutputStream()) {
                    
                    byte[] buffer = new byte[8192]; // 8KB buffer
                    int bytesRead;
                    
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    
                    outputStream.flush();
                }
            } else {
                // 如果类路径也找不到，返回404
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
    
    /**
     * 根据文件扩展名获取MIME类型
     */
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
                return "image/jpeg"; // 默认使用jpeg
        }
    }
    
    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return fileName.substring(lastDotIndex + 1);
        }
        return "";
    }
}
