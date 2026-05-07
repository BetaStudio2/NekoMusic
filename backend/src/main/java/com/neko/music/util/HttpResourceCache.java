package com.neko.music.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * 为音频/图片等磁盘资源设置 ETag、Last-Modified、Cache-Control，并处理 If-None-Match → 304。
 */
public final class HttpResourceCache {

    /** 用户侧可缓存，上传替换后通过 ETag 失效 */
    public static final String CACHE_CONTROL_FILE = "private, max-age=3600, must-revalidate";

    /** 内嵌默认图标，内容不变 */
    public static final String DEFAULT_ICON_ETAG = "\"DefaultIcon-v1\"";
    public static final String CACHE_CONTROL_DEFAULT_ICON = "public, max-age=86400, immutable";

    private HttpResourceCache() {
    }

    public static String strongEtagForFile(Path path) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
        long size = attrs.size();
        long lm = attrs.lastModifiedTime().toMillis();
        return "\"" + Long.toHexString(lm) + "-" + Long.toHexString(size) + "\"";
    }

    private static String stripEtagValue(String raw) {
        if (raw == null) {
            return "";
        }
        String v = raw.trim();
        if (v.startsWith("W/")) {
            v = v.substring(2).trim();
        }
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            v = v.substring(1, v.length() - 1);
        }
        return v;
    }

    /**
     * If-None-Match 与当前 ETag 是否匹配（支持逗号分隔的多值与 W/ 弱标签）。
     */
    public static boolean ifNoneMatchEquals(HttpServletRequest request, String etag) {
        String inm = request.getHeader("If-None-Match");
        if (inm == null || inm.isEmpty()) {
            return false;
        }
        if ("*".equals(inm.trim())) {
            return true;
        }
        String normalized = stripEtagValue(etag);
        for (String part : inm.split(",")) {
            if (stripEtagValue(part).equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 若客户端缓存仍新鲜，发送 304 并返回 true。
     */
    public static boolean sendNotModifiedIfFresh(HttpServletRequest request, HttpServletResponse response, String etag) {
        if (!ifNoneMatchEquals(request, etag)) {
            return false;
        }
        response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
        response.setHeader("ETag", etag);
        response.setHeader("Cache-Control", CACHE_CONTROL_FILE);
        return true;
    }

    public static void applyFileCachingHeaders(Path path, HttpServletResponse response) throws IOException {
        String etag = strongEtagForFile(path);
        response.setHeader("ETag", etag);
        response.setDateHeader("Last-Modified", Files.getLastModifiedTime(path).toMillis());
        response.setHeader("Cache-Control", CACHE_CONTROL_FILE);
    }

    public static boolean sendNotModifiedDefaultIcon(HttpServletRequest request, HttpServletResponse response) {
        if (!ifNoneMatchEquals(request, DEFAULT_ICON_ETAG)) {
            return false;
        }
        response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
        response.setHeader("ETag", DEFAULT_ICON_ETAG);
        response.setHeader("Cache-Control", CACHE_CONTROL_DEFAULT_ICON);
        return true;
    }

    public static void applyDefaultIconCachingHeaders(HttpServletResponse response) {
        response.setHeader("ETag", DEFAULT_ICON_ETAG);
        response.setHeader("Cache-Control", CACHE_CONTROL_DEFAULT_ICON);
    }
}
