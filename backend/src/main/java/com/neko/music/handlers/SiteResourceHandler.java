package com.neko.music.handlers;

import com.neko.music.util.SiteResourceStorage;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** 从运行目录的 site 文件夹提供前端资源，并支持 Vue History 路由回退。 */
public final class SiteResourceHandler extends HttpServlet {
    private Path siteRoot;

    @Override
    public void init() throws ServletException {
        siteRoot = SiteResourceStorage.storageDir();
        if (!Files.isDirectory(siteRoot)) {
            throw new ServletException("前端站点目录不存在: " + siteRoot);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        serve(request, response, false);
    }

    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response) throws IOException {
        serve(request, response, true);
    }

    private void serve(HttpServletRequest request, HttpServletResponse response, boolean headOnly)
            throws IOException {
        String requestPath = request.getPathInfo();
        if (requestPath == null || requestPath.isBlank()) {
            requestPath = request.getRequestURI();
        }
        if (requestPath == null || requestPath.isBlank()) {
            requestPath = "/";
        }

        Path resource = resolve(requestPath);
        boolean fallback = false;
        if (resource == null && isSpaRoute(requestPath)) {
            resource = siteRoot.resolve("index.html");
            fallback = true;
        }
        if (resource == null || !Files.isRegularFile(resource)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        long size = Files.size(resource);
        String contentType = Files.probeContentType(resource);
        if (contentType == null) {
            contentType = contentTypeFor(resource);
        }
        if (contentType != null) {
            response.setContentType(contentType);
        }
        response.setContentLengthLong(size);
        if (fallback || resource.getFileName().toString().equals("index.html")) {
            response.setHeader("Cache-Control", "no-cache");
        } else if (requestPath.startsWith("/assets/")) {
            response.setHeader("Cache-Control", "public, max-age=31536000, immutable");
        }
        if (headOnly) {
            return;
        }
        try (OutputStream output = response.getOutputStream()) {
            Files.copy(resource, output);
        }
    }

    private Path resolve(String requestPath) {
        if (!requestPath.startsWith("/") || requestPath.indexOf('\\') >= 0
                || requestPath.indexOf('\0') >= 0) {
            return null;
        }
        String relativeName = requestPath.substring(1);
        if (relativeName.isEmpty()) {
            relativeName = "index.html";
        }
        Path relative;
        try {
            relative = Path.of(relativeName).normalize();
        } catch (RuntimeException e) {
            return null;
        }
        if (relative.isAbsolute() || relative.startsWith("..")) {
            return null;
        }
        Path resolved = siteRoot.resolve(relative).normalize();
        if (!resolved.startsWith(siteRoot) || !Files.isRegularFile(resolved)) {
            return null;
        }
        return resolved;
    }

    private static boolean isSpaRoute(String requestPath) {
        if (requestPath.startsWith("/api/") || requestPath.equals("/api")
                || requestPath.startsWith("/.well-known/")) {
            return false;
        }
        int queryStart = requestPath.indexOf('?');
        String path = queryStart >= 0 ? requestPath.substring(0, queryStart) : requestPath;
        int slash = path.lastIndexOf('/');
        return !path.substring(slash + 1).contains(".");
    }

    private static String contentTypeFor(Path resource) {
        String name = resource.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".html")) return "text/html; charset=UTF-8";
        if (name.endsWith(".css")) return "text/css; charset=UTF-8";
        if (name.endsWith(".js")) return "text/javascript; charset=UTF-8";
        if (name.endsWith(".json")) return "application/json; charset=UTF-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".ico")) return "image/x-icon";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }
}
