package com.neko.music.handlers;

import com.neko.music.util.ClientAborts;

import com.neko.music.Main;
import com.neko.music.util.HttpResourceCache;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class UserAvatarHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(UserAvatarHandler.class);

    /** 拷贝缓冲区：默认头像约 300KB，8KB 缓冲要写四十来次，白白放大成多次系统调用。 */
    private static final int COPY_BUFFER_SIZE = 64 * 1024;

    /**
     * 内嵌默认头像字节。
     *
     * <p>类路径资源在 jar 内是压缩存储的，逐次 {@code getResourceAsStream} 都要重新解压约 300KB；
     * 这张图内容恒定，只读一次常驻内存即可，之后每个请求只是写一次 Content-Length 再拷贝。</p>
     */
    private static volatile byte[] defaultIconBytes;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        
        if (pathInfo == null || pathInfo.equals("/") || pathInfo.equals("/default")) {
            // URL 本身就表示「要默认头像」，内容恒定，可以长期缓存
            sendDefaultIcon(request, response, true);
            return;
        }
        
        // 解析用户ID (路径格式: /{userId} 或 /{userId}/avatar)
        String[] pathParts = pathInfo.split("/");
        String userIdStr = pathParts.length > 1 ? pathParts[1] : null;
        
        if (userIdStr == null || userIdStr.isEmpty()) {
            sendDefaultIcon(request, response, true);
            return;
        }
        
        // 尝试解析用户ID
        int userId;
        try {
            userId = Integer.parseInt(userIdStr);
        } catch (NumberFormatException e) {
            logger.warn("无效的用户ID: " + userIdStr);
            sendDefaultIcon(request, response, true);
            return;
        }
        
        // 从数据库获取用户头像路径
        String avatarPath = getUserAvatarPath(userId);
        
        if (avatarPath != null && !avatarPath.isEmpty()) {
            // 只 stat 一次：存在性、大小、修改时间都从同一份属性取，别为判存在再多打两次系统调用
            Path avatarFile = Paths.get(avatarPath);
            BasicFileAttributes attrs = null;
            try {
                attrs = Files.readAttributes(avatarFile, BasicFileAttributes.class);
            } catch (IOException ignored) {
                // 文件不存在 / 不可读：按「没有头像」处理
            }
            if (attrs != null && attrs.isRegularFile()) {
                sendImageFile(request, avatarFile, attrs, response);
                return;
            }
            logger.warn("用户头像文件不存在: {}", avatarPath);
        }
        
        // 该 URL 之后可能换成真头像，不能长期缓存，必须让浏览器每次再校验
        sendDefaultIcon(request, response, false);
    }
    
    /**
     * 从数据库获取用户头像路径
     */
    private String getUserAvatarPath(int userId) {
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            String sql = "SELECT avatar FROM users WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("avatar");
                    }
                }
            }
        } catch (Exception e) {
            logger.error("获取用户头像路径时出错", e);
        }
        return null;
    }
    
    /**
     * 发送内嵌默认头像。
     *
     * @param immutable 该 URL 是否恒定指向这张默认图（{@code /default}）：是则可长期公共缓存；
     *                  否则（用户暂无头像）只允许浏览器私有缓存并逐次再校验，换图后立刻生效
     */
    private void sendDefaultIcon(HttpServletRequest request, HttpServletResponse response, boolean immutable)
            throws IOException {
        String cacheControl = immutable
                ? HttpResourceCache.CACHE_CONTROL_DEFAULT_ICON
                : HttpResourceCache.CACHE_CONTROL_PRIVATE_REVALIDATE;
        if (HttpResourceCache.sendNotModifiedIfFresh(request, response,
                HttpResourceCache.DEFAULT_ICON_ETAG, cacheControl)) {
            return;
        }

        byte[] icon = defaultIconBytes();
        if (icon.length == 0) {
            // 类路径里没有这张图（打包异常）：回 404，而不是 200 + 空 body
            response.setStatus(HttpStatus.NOT_FOUND_404);
            response.setContentType("text/plain;charset=utf-8");
            response.getWriter().println("Default icon not found");
            return;
        }

        response.setContentType("image/png");
        response.setStatus(HttpStatus.OK_200);
        // 显式给长度：不给就退回 chunked，Jetty 按缓冲切成多次写，小图也白慢
        response.setContentLength(icon.length);
        HttpResourceCache.applyDefaultIconCachingHeaders(response, cacheControl);
        response.getOutputStream().write(icon);
    }

    /** 默认头像字节：首次从类路径读入后常驻内存；读取失败缓存为空数组，后续请求直接 404。 */
    private static byte[] defaultIconBytes() {
        byte[] cached = defaultIconBytes;
        if (cached != null) {
            return cached;
        }
        synchronized (UserAvatarHandler.class) {
            if (defaultIconBytes == null) {
                byte[] loaded = new byte[0];
                try (InputStream in = UserAvatarHandler.class.getClassLoader()
                        .getResourceAsStream("DefaultIcon.png")) {
                    if (in != null) {
                        loaded = in.readAllBytes();
                    }
                } catch (IOException e) {
                    logger.error("读取默认头像失败", e);
                }
                defaultIconBytes = loaded;
            }
            return defaultIconBytes;
        }
    }

    /**
     * 发送用户头像文件：带 ETag / Last-Modified，命中 {@code If-None-Match} 时回 304。
     *
     * <p>头像是「同一 URL 内容可能被替换」的资源，因此用 {@code private, max-age=0,
     * must-revalidate}：浏览器可以留一份副本，但每次显示前都要回源校验，换图后立刻生效；
     * 重复浏览只走一次 304，不会再重复拖几百 KB。文件属性由调用方一次 stat 传入，这里不再读盘。</p>
     */
    private void sendImageFile(HttpServletRequest request, Path imagePath, BasicFileAttributes attrs,
                               HttpServletResponse response) throws IOException {
        String cacheControl = HttpResourceCache.CACHE_CONTROL_PRIVATE_REVALIDATE;
        String etag = HttpResourceCache.etagFor(attrs);
        if (HttpResourceCache.sendNotModifiedIfFresh(request, response, etag, cacheControl)) {
            return;
        }

        response.setContentType(getMimeType(imagePath.toString()));
        response.setStatus(HttpStatus.OK_200);
        response.setContentLengthLong(attrs.size());
        HttpResourceCache.applyFileCachingHeaders(attrs, response, cacheControl);

        try (InputStream inputStream = Files.newInputStream(imagePath);
             OutputStream outputStream = response.getOutputStream()) {
            copyStream(inputStream, outputStream);
        } catch (IOException e) {
            if (ClientAborts.isClientAbort(e)) {
                logger.debug("客户端在头像发送完成前断开连接: {}", imagePath);
                return;
            }
            throw e;
        }
    }

    private void copyStream(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        int bytesRead;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }

        outputStream.flush();
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
                return "image/png"; // 默认使用png
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
