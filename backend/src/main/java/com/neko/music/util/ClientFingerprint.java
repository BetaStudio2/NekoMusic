package com.neko.music.util;

import com.neko.music.seo.UserAgentClassifier;

/**
 * 从 User-Agent 与 {@code X-Neko-Client} 头提取「客户端指纹」，写入访问日志供安全分析。
 *
 * <p>判定全部复用 {@link UserAgentClassifier} 的既有名单，不另立一套关键词，避免防爬策略与日志口径漂移。
 * 类型、平台、版本三个字段都同时看 UA 与 {@code X-Neko-Client}：UA 可被中间层改写，自报平台可伪造，
 * 两个信号互相印证才能既认得出自家客户端、又不给脚本开口子（顺序见
 * {@link #type(String, String)}）。</p>
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
     * 客户端类型。判定用两个信号：UA 与 {@code X-Neko-Client}（三端都会同时发）。
     *
     * <p>为什么要看 {@code X-Neko-Client}：UA 是三样东西里最容易被中间层改写的——企业代理、
     * Android 系统 WebView 包装、抓包 / 下载工具都可能把官方客户端的 UA 换成库默认值（{@code okhttp/…}、
     * 空、被截断），单看 UA 就会把自家客户端记成第三方甚至爬虫。官方客户端的 UA 形态与自报平台本来
     * 就是一一对应的，两个信号一起看才不会因一次改写就丢失身份。</p>
     *
     * <p>顺序不能调换：<b>先黑名单、后自报</b>。{@code X-Neko-Client} 是客户端自己写的，随手就能伪造，
     * {@code sqlmap/1.7} 加一个 {@code android+1} 不能因此变成官方客户端。因此固定为
     * 安全扫描器 → 爬虫 → 真浏览器 → 自报官方平台 → 官方 UA 锚定 → 其它客户端。</p>
     *
     * <p>自报平台要求与 UA 不矛盾：UA 带浏览器内核标记时按浏览器记（浏览器不可能是 PC / Android
     * 原生客户端）；UA 已锚定为官方形态时本就走官方客户端。只有「UA 看不出身份 + 自报官方平台」
     * 才由自报决定。</p>
     */
    public static String type(String userAgent, String nekoClient) {
        if (userAgent == null || userAgent.isBlank()) {
            return UNKNOWN;
        }
        if (UserAgentClassifier.isSecurityScanner(userAgent)) {
            return SCANNER;
        }
        if (UserAgentClassifier.isBotForApi(userAgent)) {
            return BOT;
        }
        if (UserAgentClassifier.looksLikeRealBrowser(userAgent)) {
            return BROWSER;
        }
        String[] declared = parseNekoClient(nekoClient);
        if (declared != null && UserAgentClassifier.isOfficialClientPlatform(declared[0])) {
            return OFFICIAL_CLIENT;
        }
        return UserAgentClassifier.isNativeClient(userAgent) ? OFFICIAL_CLIENT : THIRD_PARTY;
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
