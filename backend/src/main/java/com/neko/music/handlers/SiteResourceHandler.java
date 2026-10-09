package com.neko.music.handlers;

import com.neko.music.util.HttpResourceCache;
import com.neko.music.util.SiteResourceStorage;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * 直接从 classpath（JAR 内嵌的 {@code site/}）提供前端资源，并支持 Vue History 路由回退。
 *
 * <p>不解压到运行目录：前端只有后端这一个来源，磁盘上不留副本，也不存在被别的 Web 服务器直接
 * 托管、绕过后端判定的旁路。</p>
 */
public final class SiteResourceHandler extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(SiteResourceHandler.class);

    private Map<String, SiteResourceStorage.Entry> siteIndex = Map.of();

    @Override
    public void init() throws ServletException {
        try {
            siteIndex = SiteResourceStorage.index();
        } catch (IOException e) {
            throw new ServletException("前端站点资源加载失败", e);
        }
        if (siteIndex.isEmpty()) {
            // 不阻断启动：缺前端时页面 404，但 API 依旧可用。
            logger.error("classpath 内未包含前端站点资源（site/），页面请求将一律 404");
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
        String requestPath = resolveRequestPath(request);

        String resourcePath = normalize(requestPath);
        SiteResourceStorage.Entry entry = resourcePath == null ? null : siteIndex.get(resourcePath);
        boolean fallback = false;
        if (entry == null && isSpaRoute(requestPath)) {
            entry = siteIndex.get("/index.html");
            fallback = true;
        }
        if (entry == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String fileName = fileNameOf(entry.path());

        // index.html / Service Worker 必须可及时更新：缓存住入口或 sw.js，
        // 新版本会迟迟无法被发现。这类文件用 no-cache + ETag/Last-Modified
        // 允许 304 再校验，避免整文件重下。
        boolean mustRevalidate = fallback || fileName.endsWith(".html")
                || fileName.equals("sw.js");
        if (mustRevalidate) {
            response.setHeader("Cache-Control", "no-cache");
        } else if (fileName.endsWith(".txt")) {
            // robots.txt / llms*.txt 等：一天
            response.setHeader("Cache-Control",
                    "public, max-age=" + HttpResourceCache.MAX_AGE_ONE_DAY);
        } else if (entry.path().startsWith("/assets/")) {
            // Vite 构建产物带内容哈希，可安全 immutable
            response.setHeader("Cache-Control",
                    "public, max-age=" + HttpResourceCache.MAX_AGE_SIX_MONTHS + ", immutable");
        } else {
            // 其它固定资源（png/ico/svg/webmanifest/js/css/字体等）：六个月
            response.setHeader("Cache-Control",
                    "public, max-age=" + HttpResourceCache.MAX_AGE_SIX_MONTHS);
        }

        // 所有站点资源都带内容摘要式 ETag，条件请求可走 304，避免整包回传。
        response.setHeader("ETag", entry.etag());
        if (entry.lastModified() > 0) {
            response.setDateHeader("Last-Modified", entry.lastModified());
        }
        if (HttpResourceCache.ifNoneMatchEquals(request, entry.etag())) {
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }

        String contentType = contentTypeFor(fileName);
        if (contentType != null) {
            response.setContentType(contentType);
        }
        response.setContentLength(entry.content().length);
        if (headOnly) {
            return;
        }
        response.getOutputStream().write(entry.content());
    }

    /** 取请求路径：默认映射下 pathInfo 为空，回退到 requestURI 并去掉 context path。 */
    private static String resolveRequestPath(HttpServletRequest request) {
        String path = request.getPathInfo();
        if (path != null && !path.isBlank()) {
            return path;
        }
        String uri = request.getRequestURI();
        if (uri == null || uri.isBlank()) {
            return "/";
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        return uri.isEmpty() ? "/" : uri;
    }

    /**
     * 把请求路径归一成资源键；不可用时返回 null。
     *
     * <p>索引本身就是站点内文件的精确清单，只做全等查表，因此即便路径里带 {@code ..} 也只会
     * 查不中而 404，不存在目录穿越。</p>
     */
    private static String normalize(String requestPath) {
        if (requestPath == null || requestPath.isEmpty()) {
            return null;
        }
        int queryStart = requestPath.indexOf('?');
        String path = queryStart >= 0 ? requestPath.substring(0, queryStart) : requestPath;
        if (!path.startsWith("/") || path.indexOf('\\') >= 0 || path.indexOf('\0') >= 0) {
            return null;
        }
        String normalized;
        try {
            normalized = Path.of(path).normalize().toString().replace(File.separatorChar, '/');
        } catch (RuntimeException e) {
            return null;
        }
        if (!normalized.startsWith("/")) {
            return null;
        }
        if (normalized.equals("/")) {
            return "/index.html";
        }
        return normalized.endsWith("/") ? normalized + "index.html" : normalized;
    }

    private static String fileNameOf(String resourcePath) {
        int slash = resourcePath.lastIndexOf('/');
        return resourcePath.substring(slash + 1).toLowerCase(Locale.ROOT);
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

    private static String contentTypeFor(String fileName) {
        if (fileName.endsWith(".html")) return "text/html; charset=UTF-8";
        if (fileName.endsWith(".css")) return "text/css; charset=UTF-8";
        if (fileName.endsWith(".js") || fileName.endsWith(".mjs")) return "text/javascript; charset=UTF-8";
        if (fileName.endsWith(".json") || fileName.endsWith(".map")) return "application/json; charset=UTF-8";
        if (fileName.endsWith(".txt")) return "text/plain; charset=UTF-8";
        if (fileName.endsWith(".webmanifest")) return "application/manifest+json; charset=UTF-8";
        if (fileName.endsWith(".svg")) return "image/svg+xml";
        if (fileName.endsWith(".ico")) return "image/x-icon";
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
        if (fileName.endsWith(".png")) return "image/png";
        if (fileName.endsWith(".webp")) return "image/webp";
        if (fileName.endsWith(".gif")) return "image/gif";
        if (fileName.endsWith(".woff2")) return "font/woff2";
        if (fileName.endsWith(".woff")) return "font/woff";
        if (fileName.endsWith(".ttf")) return "font/ttf";
        if (fileName.endsWith(".otf")) return "font/otf";
        if (fileName.endsWith(".wasm")) return "application/wasm";
        // 未知扩展名：交给容器/浏览器按字节推断
        return null;
    }
}
