package com.neko.music.util;

import com.neko.music.seo.UserAgentClassifier;

/**
 * 从 User-Agent 与 {@code X-Neko-Client} 头提取「客户端指纹」，写入访问日志供安全分析。
 *
 * <p>判定全部复用 {@link UserAgentClassifier} 的既有名单，不另立一套关键词，避免防爬策略与日志口径漂移。
 */
public final class ClientFingerprint {

    public static final String BROWSER = "browser";
    public static final String OFFICIAL_CLIENT = "official_client";
    public static final String THIRD_PARTY = "third_party";
    public static final String BOT = "bot";
    public static final String SCANNER = "scanner";
    public static final String UNKNOWN = "unknown";

    /** {@code X-Neko-Client: <平台>+<版本>} 的取值上限，超长或非法一律忽略。 */
    private static final int MAX_PLATFORM_LENGTH = 24;
    private static final int MAX_VERSION_LENGTH = 32;

    private ClientFingerprint() {
    }

    /**
     * 客户端类型。顺序不能调换：先认官方客户端（UA 已严格锚定），再把「安全扫描器」从通用爬虫里
     * 单独摘出来，最后才按浏览器特征头区分真实浏览器与其它客户端。
     */
    public static String type(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return UNKNOWN;
        }
        if (UserAgentClassifier.isNativeClient(userAgent)) {
            return OFFICIAL_CLIENT;
        }
        if (UserAgentClassifier.isSecurityScanner(userAgent)) {
            return SCANNER;
        }
        if (UserAgentClassifier.isBotForApi(userAgent)) {
            return BOT;
        }
        return UserAgentClassifier.looksLikeRealBrowser(userAgent) ? BROWSER : THIRD_PARTY;
    }

    /** 客户端平台：官方客户端 UA → {@code X-Neko-Client} → 浏览器统一记 web；无法判定返回 null。 */
    public static String platform(String userAgent, String nekoClient) {
        String official = UserAgentClassifier.officialClientPlatform(userAgent);
        if (!official.isEmpty()) {
            return official;
        }
        String[] parts = parseNekoClient(nekoClient);
        if (parts != null) {
            return parts[0];
        }
        return looksLikeBrowser(userAgent) ? "web" : null;
    }

    /** 客户端版本：官方客户端 UA → {@code X-Neko-Client}；无法判定返回 null。 */
    public static String version(String userAgent, String nekoClient) {
        String official = UserAgentClassifier.officialClientVersion(userAgent);
        if (!official.isEmpty()) {
            return official;
        }
        String[] parts = parseNekoClient(nekoClient);
        return parts == null ? null : parts[1];
    }

    private static boolean looksLikeBrowser(String userAgent) {
        return userAgent != null && UserAgentClassifier.looksLikeRealBrowser(userAgent);
    }

    /** 解析 {@code <平台>+<版本>}；格式不对返回 null（不把可疑内容写进日志库）。 */
    static String[] parseNekoClient(String nekoClient) {
        if (nekoClient == null || nekoClient.isBlank()) {
            return null;
        }
        String value = nekoClient.trim();
        if (value.length() > MAX_PLATFORM_LENGTH + MAX_VERSION_LENGTH + 1) {
            return null;
        }
        int plus = value.indexOf('+');
        if (plus <= 0 || plus == value.length() - 1) {
            return null;
        }
        String platform = value.substring(0, plus);
        String version = value.substring(plus + 1);
        if (!isSafeToken(platform, MAX_PLATFORM_LENGTH) || !isSafeToken(version, MAX_VERSION_LENGTH)) {
            return null;
        }
        return new String[] { platform.toLowerCase(java.util.Locale.ROOT), version };
    }

    private static boolean isSafeToken(String value, int maxLength) {
        if (value.isEmpty() || value.length() > maxLength) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /**
     * 同网段聚合键：IPv4 取 /24，IPv6 取前 3 段 /48。用于把 CGI 共享出口（如运营商 CGNAT）的
     * 大量请求聚成一个来源，观察整体速率而不是一万个孤立 IP。无法判定时返回 null。
     */
    public static String ipSubnet(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        if (ip.indexOf(':') >= 0) {
            String[] parts = ip.split(":");
            StringBuilder builder = new StringBuilder();
            int taken = 0;
            for (String part : parts) {
                if (part.isEmpty()) {
                    break;
                }
                if (taken > 0) {
                    builder.append(':');
                }
                builder.append(part);
                taken++;
                if (taken == 3) {
                    break;
                }
            }
            return taken == 0 ? null : builder.append("::/48").toString();
        }
        String[] octets = ip.split("\\.");
        if (octets.length != 4) {
            return null;
        }
        return octets[0] + "." + octets[1] + "." + octets[2] + ".0/24";
    }
}
