package com.neko.music.util;

import java.util.Locale;
import java.util.Set;

/**
 * 请求路径分类：为了访问日志的「访问量统计」能按类别聚合（页面 / 动态接口 / 媒体 / 静态资源 / 后台）。
 *
 * <p>必须使用 {@link RequestPath#of} 给出的「路由视角」路径，而不是原始 URI，否则
 * {@code /./api/x} 这类等价写法会被归到错误的类别。</p>
 */
public final class RequestCategory {

    public static final String ADMIN = "admin";
    public static final String API = "api";
    public static final String MEDIA = "media";
    public static final String STATIC = "static";
    public static final String PAGE = "page";
    public static final String OTHER = "other";

    /** 常见静态资源扩展名（小写，含点）。 */
    private static final Set<String> STATIC_EXTENSIONS = Set.of(
            ".js", ".mjs", ".css", ".map", ".png", ".jpg", ".jpeg", ".gif", ".webp", ".avif",
            ".ico", ".svg", ".woff", ".woff2", ".ttf", ".otf", ".eot",
            ".exe", ".pak", ".deb", ".apk", ".dmg", ".zip", ".bin", ".txt", ".xml", ".json",
            ".webmanifest", ".wasm");

    private RequestCategory() {
    }

    public static String of(String path) {
        if (path == null || path.isEmpty()) {
            return OTHER;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.equals("/api/admin") || lower.startsWith("/api/admin/")) {
            return ADMIN;
        }
        if (lower.equals("/api") || lower.startsWith("/api/") || lower.equals("/loser")
                || lower.startsWith("/loser/")) {
            return API;
        }
        if (lower.startsWith("/media/") || lower.startsWith("/update/")) {
            return MEDIA;
        }
        if (lower.startsWith("/assets/")) {
            return STATIC;
        }
        int dot = lower.lastIndexOf('.');
        if (dot > 0 && STATIC_EXTENSIONS.contains(lower.substring(dot))) {
            return STATIC;
        }
        if (lower.startsWith("/detail/")) {
            return PAGE;
        }
        // 无扩展名的路径一律按页面处理（SPA 路由），其余归 other
        return lower.contains(".") ? OTHER : PAGE;
    }
}
