package com.neko.music.util;

import com.neko.music.Main;
import jakarta.servlet.http.HttpServletRequest;

/** 从反代请求头或既有业务配置解析对外站点根 URL（无尾斜杠） */
public final class SiteUrlResolver {
    private SiteUrlResolver() {}

    public static String resolvePublicSiteBase(HttpServletRequest request) {
        String host = firstNonBlank(
                headerFirst(request, "X-Forwarded-Host"),
                request.getHeader("Host")
        );
        if (host != null && !host.isBlank()) {
            host = host.split(",")[0].trim();
            String proto = firstNonBlank(request.getHeader("X-Forwarded-Proto"), request.getScheme());
            if (proto == null || proto.isBlank()) {
                proto = "https";
            }
            proto = proto.split(",")[0].trim().toLowerCase();
            return proto + "://" + host;
        }
        return Main.getConfigManager().getVideoRenderNotifyFrontendBaseUrl();
    }

    private static String headerFirst(HttpServletRequest request, String name) {
        String v = request.getHeader(name);
        if (v == null || v.isBlank()) {
            return null;
        }
        return v.split(",")[0].trim();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }
}
