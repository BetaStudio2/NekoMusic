package com.neko.music.util;

import com.neko.music.Main;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.regex.Pattern;

/** 从反代请求头或既有业务配置解析对外站点根 URL（无尾斜杠） */
public final class SiteUrlResolver {
    private SiteUrlResolver() {}
    private static final Pattern HOST_PORT_PATTERN = Pattern.compile(
            "^(?:[a-zA-Z0-9-]+\\.)*[a-zA-Z0-9-]+(?::\\d{1,5})?$"
    );

    public static String resolvePublicSiteBase(HttpServletRequest request) {
        String host = normalizeHost(firstNonBlank(
                headerFirst(request, "X-Forwarded-Host"),
                request.getHeader("Host")
        ));
        if (host != null) {
            String proto = normalizeProto(firstNonBlank(request.getHeader("X-Forwarded-Proto"), request.getScheme()));
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

    private static String normalizeProto(String proto) {
        if (proto == null || proto.isBlank()) {
            return "https";
        }
        String p = proto.split(",")[0].trim().toLowerCase(Locale.ROOT);
        if ("http".equals(p) || "https".equals(p)) {
            return p;
        }
        return "https";
    }

    private static String normalizeHost(String host) {
        if (host == null || host.isBlank()) {
            return null;
        }
        String h = host.split(",")[0].trim().toLowerCase(Locale.ROOT);
        if (!HOST_PORT_PATTERN.matcher(h).matches()) {
            return null;
        }
        return h;
    }
}
