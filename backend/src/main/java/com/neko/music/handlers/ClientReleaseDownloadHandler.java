package com.neko.music.handlers;

import com.neko.music.util.ClientReleaseStorage;
import com.neko.music.util.HttpResourceCache;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** 客户端安装包直链：{@code GET /update/{文件名}} */
public class ClientReleaseDownloadHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(ClientReleaseDownloadHandler.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.isEmpty() || "/".equals(pathInfo)) {
            response.setStatus(HttpStatus.NOT_FOUND_404);
            response.setContentType("text/plain;charset=utf-8");
            response.getWriter().println("请指定安装包文件名，例如 /update/20260207-35.apk");
            return;
        }

        String rawName = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        String fileName = URLDecoder.decode(rawName, StandardCharsets.UTF_8);

        Optional<Path> fileOpt = ClientReleaseStorage.resolveReadableFile(fileName);
        if (fileOpt.isEmpty()) {
            response.setStatus(HttpStatus.NOT_FOUND_404);
            response.setContentType("text/plain;charset=utf-8");
            response.getWriter().println("安装包不存在: " + fileName);
            logger.debug("安装包未找到: {}", fileName);
            return;
        }

        Path file = fileOpt.get();
        String etag = HttpResourceCache.strongEtagForFile(file);
        if (HttpResourceCache.sendNotModifiedIfFresh(request, response, etag)) {
            return;
        }

        response.setStatus(HttpStatus.OK_200);
        response.setContentType(ClientReleaseStorage.contentTypeForFileName(fileName));
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
                + java.net.URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20"));
        HttpResourceCache.applyFileCachingHeaders(file, response);
        response.setHeader("Cache-Control", "public, max-age=3600, must-revalidate");
        response.setContentLengthLong(Files.size(file));

        try (InputStream in = Files.newInputStream(file);
             OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
            out.flush();
        }
        logger.info("已提供安装包下载: {}", fileName);
    }
}
