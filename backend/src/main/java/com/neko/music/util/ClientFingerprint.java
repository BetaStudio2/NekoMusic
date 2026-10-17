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

    /** 站内 Web 前端自报的平台取值：运行在浏览器里，归入浏览器类型。 */
    private static final String WEB_PLATFORM = "web";

    /** {@code X-Neko-Client: <平台>+<版本>} 的取值上限，超长或非法一律忽略。 */
    private static final int MAX_PLATFORM_LENGTH = 24;
    private static final int MAX_VERSION_LENGTH = 32;

    private ClientFingerprint() {
    }

    /**
     * 客户端类型。判定用两个信号：UA 与 {@code X-Neko-Client}（三端都会同时发）。
     *
     * <p>为什么要看 {@code X-Neko-Client}：UA 是三样东西里最容易被中间层改写的——企业代理、
     * Android 系统 WebView 包装、抓包 / 下载工具都可能把官方客户端的 UA 换成库默认值（{@code okhttp/…}）
     * 甚至整段浏览器 UA。只按 UA 判定，自家客户端就会在日志里被记成浏览器或第三方。
     * {@code X-Neko-Client} 是客户端自己声明的平台，正好用来纠正这类被改写的 UA。</p>
     *
     * <p>顺序不能调换：<b>先黑名单，后自报</b>。{@code X-Neko-Client} 是客户端自己写的，随手就能伪造，
     * {@code sqlmap/1.7} 加一个 {@code android+1} 不能因此变成官方客户端。因此固定为
     * 安全扫描器 → 爬虫 → 自报官方平台 → 真浏览器 → 官方 UA 锚定 → 其它客户端。
     * 自报平台只在「UA 没命中黑名单」时才采信；空 / 缺失 UA 一律按爬虫口径处理，不因自报豁免。</p>
     *
     * <p>自报平台优先于浏览器启发式：官方客户端的 UA 被中间层换成浏览器形态时（系统 WebView、
     * QtWebEngine、公司代理），只要自报平台是官方平台，仍按官方客户端记录。{@code web+<版本>} 是站内
     * 前端在浏览器里注入的，不算原生客户端，仍按浏览器记。</p>
     */
    public static String type(String userAgent, String nekoClient) {
        return fingerprint(userAgent, nekoClient).type();
    }

    /**
     * 客户端平台：自报官方平台 → 官方客户端 UA → 自报平台 → 浏览器统一记 web。
     *
     * <p>{@code X-Neko-Client} 报的是官方平台时优先于 UA：UA 被中间层换成浏览器形态时，
     * 这里必须跟 {@link #type(String, String)} 一致仍报原生平台，不能一个字段记官方客户端、
     * 另一个字段记 web。{@code web+<版本>} 仍算浏览器。无法判定返回 null。</p>
     */
    public static String platform(String userAgent, String nekoClient) {
        return fingerprint(userAgent, nekoClient).platform();
    }

    /** 客户端版本：与 {@link #platform(String, String)} 同源，保证平台与版本成对取自同一信号。 */
    public static String version(String userAgent, String nekoClient) {
        return fingerprint(userAgent, nekoClient).version();
    }

    /** 一次算完类型 / 平台 / 版本（三者共用同一套 UA 判定，分开算等于把同样的判断做三遍）。 */
    public record Fingerprint(String userAgent, String nekoClient,
                              String type, String platform, String version) {
    }

    private static final String SCOPE_KEY = "clientFingerprint";

    /**
     * 客户端指纹：访问日志要同时写类型 / 平台 / 版本三列，这里按「本次请求的 UA + 自报平台」
     * 只算一次并缓存在 {@link RequestScope}（请求结束后自然释放，不跨请求保留客户端信息）。
     */
    public static Fingerprint fingerprint(String userAgent, String nekoClient) {
        Fingerprint cached = RequestScope.get(SCOPE_KEY);
        if (cached != null
                && java.util.Objects.equals(cached.userAgent(), userAgent)
                && java.util.Objects.equals(cached.nekoClient(), nekoClient)) {
            return cached;
        }
        Fingerprint computed = new Fingerprint(userAgent, nekoClient,
                computeType(userAgent, nekoClient),
                computePlatform(userAgent, nekoClient),
                computeVersion(userAgent, nekoClient));
        RequestScope.put(SCOPE_KEY, computed);
        return computed;
    }

    private static String computeType(String userAgent, String nekoClient) {
        if (userAgent == null || userAgent.isBlank()) {
            return UNKNOWN;
        }
        if (UserAgentClassifier.isSecurityScanner(userAgent)) {
            return SCANNER;
        }
        if (UserAgentClassifier.isBotForApi(userAgent)) {
            return BOT;
        }
        // UA 没命中黑名单时才采信自报平台：官方平台记官方客户端，web 记浏览器。
        String[] declared = parseNekoClient(nekoClient);
        if (declared != null) {
            if (UserAgentClassifier.isOfficialClientPlatform(declared[0])) {
                return OFFICIAL_CLIENT;
            }
            if (WEB_PLATFORM.equals(declared[0])) {
                return BROWSER;
            }
        }
        if (UserAgentClassifier.looksLikeRealBrowser(userAgent)) {
            return BROWSER;
        }
        return UserAgentClassifier.isNativeClient(userAgent) ? OFFICIAL_CLIENT : THIRD_PARTY;
    }

    private static String computePlatform(String userAgent, String nekoClient) {
        String[] parts = parseNekoClient(nekoClient);
        if (parts != null && UserAgentClassifier.isOfficialClientPlatform(parts[0])) {
            return parts[0];
        }
        String official = UserAgentClassifier.officialClientPlatform(userAgent);
        if (!official.isEmpty()) {
            return official;
        }
        if (parts != null) {
            return parts[0];
        }
        return looksLikeBrowser(userAgent) ? WEB_PLATFORM : null;
    }

    private static String computeVersion(String userAgent, String nekoClient) {
        String[] parts = parseNekoClient(nekoClient);
        if (parts != null && UserAgentClassifier.isOfficialClientPlatform(parts[0])) {
            return parts[1];
        }
        String official = UserAgentClassifier.officialClientVersion(userAgent);
        if (!official.isEmpty()) {
            return official;
        }
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
