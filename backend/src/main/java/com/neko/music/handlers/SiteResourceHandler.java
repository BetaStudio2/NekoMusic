package com.neko.music.handlers;

import com.neko.music.util.HttpResourceCache;
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
        String fileName = resource.getFileName().toString().toLowerCase(Locale.ROOT);

        // index.html / Service Worker 必须可及时更新：缓存住入口或 sw.js，
        // 新版本会迟迟无法被发现。这类文件用 no-cache + ETag/Last-Modified
        // 允许 304 再校验，避免整文件重下。
        boolean mustRevalidate = fallback || fileName.endsWith(".html")
                || fileName.equals("sw.js");
        if (mustRevalidate) {
            String etag = HttpResourceCache.strongEtagForFile(resource);
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("ETag", etag);
            response.setDateHeader("Last-Modified", Files.getLastModifiedTime(resource).toMillis());
            if (HttpResourceCache.ifNoneMatchEquals(request, etag)) {
                response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                return;
            }
        } else if (fileName.endsWith(".txt")) {
            // robots.txt / llms*.txt 等：一天
            response.setHeader("Cache-Control",
                    "public, max-age=" + HttpResourceCache.MAX_AGE_ONE_DAY);
        } else if (requestPath.startsWith("/assets/")) {
            // Vite 构建产物带内容哈希，可安全 immutable
            response.setHeader("Cache-Control",
                    "public, max-age=" + HttpResourceCache.MAX_AGE_SIX_MONTHS + ", immutable");
        } else {
            // 其它固定资源（png/ico/svg/webmanifest/js/css/字体/安装包 .exe/.pak/.deb 等）：六个月
            response.setHeader("Cache-Control",
                    "public, max-age=" + HttpResourceCache.MAX_AGE_SIX_MONTHS);
        }

        // 已知扩展名优先用显式映射（如 .webmanifest 在部分系统上会被 probeContentType
        // 误判为 text/plain），未知扩展名再回退到系统 MIME 探测。
        String contentType = contentTypeFor(resource);
        if (contentType == null) {
            contentType = Files.probeContentType(resource);
        }
        if (contentType != null) {
            response.setContentType(contentType);
        }
        response.setContentLengthLong(size);
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
        if (name.endsWith(".txt")) return "text/plain; charset=UTF-8";
        if (name.endsWith(".webmanifest")) return "application/manifest+json; charset=UTF-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".ico")) return "image/x-icon";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".webp")) return "image/webp";
        // 未知扩展名交给 Files.probeContentType 兜底（字体等）
        return null;
    }
}
