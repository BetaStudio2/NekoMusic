package com.neko.music.util;

import com.neko.music.Main;
import com.neko.music.config.ConfigManager;
import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 可信客户端 IP 解析（引用这段逻辑的所有位置必须用它，避免各处以相同方式重复读取可伪造的转发头）。
 *
 * <p>背景：评论归属地（IpRegionService）、IP 限流、歌词、支付水皮下发、听歌识曲之前都各自信任
 * 客户端可伪造的 {@code X-Forwarded-For} / {@code X-Real-IP} / {@code Proxy-Client-IP} 头，导致：</p>
 * <ul>
 *   <li>任性客户端可把 {@code X-Forwarded-For} 改成任意 IP，评论归属地（geo）随头被「污染」；</li>
 *   <li>爬虫只要每发一个请求换一个头，限流就被拆到无数个互不相干的 IP 桶里，等于被绕过。</li>
 * </ul>
 *
 * <p>策略由 {@code network.trusted_client_ip_header} 决定：</p>
 * <ul>
 *   <li>{@code auto}（默认）—— 适配「CDN 经常换、各家头不一样」：先试
 *       {@code network.extra_client_ip_headers}（配置里额外登记的自定义头），再依次尝试 CDN 专用单值头
 *       （{@code CF-Connecting-IP} / {@code True-Client-IP} / {@code Ali-CDN-Real-IP} /
 *       {@code X-Edge-Real-IP} / {@code Fastly-Client-IP} / {@code X-Client-IP} /
 *       {@code X-Azure-ClientIP} / {@code CloudFront-Viewer-Address}），再退到 {@code X-Forwarded-For}
 *       （自动跳过本机 Nginx 追加的那一跳，只摘一格），最后退到 {@code X-Real-IP} 与 socket 对端。
 *       换 CDN 不用改代码、也不用维护回源 IP 段。
 *       为降低伪造面，只有在 socket 对端是回环 / 内网地址（说明请求确实经过我们自己的反向代理）时才信任转发头；
 *       后端被公网直连时一律使用对端地址。</li>
 *   <li>具体头名 —— 只信该头（如 {@code X-Real-IP}，即 Nginx 覆盖写入、客户端无法伪造的那一个）。</li>
 *   <li>{@code direct} / 空白 —— 纯直连公网：忽略一切转发头，只用 socket 对端地址，最安全。</li>
 * </ul>
 *
 * <p><b>取舍：</b>{@code auto} 为了兼容各家 CDN，必须信任转发头，因此当源站可以被公网直连时，
 * 伪造头仍然能改变归属地与限流分桶。默认部署里后端只监听 127.0.0.1、由本机 Nginx 回源，
 * 这条路径不成立；若把后端端口直接暴露到公网，请改回 {@code direct} 或写成具体的头名。</p>
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    /** {@code auto} 模式：明确由 CDN 写入真实客户端 IP 的单值头，按优先级依次尝试。 */
    private static final List<String> AUTO_CDN_HEADERS = List.of(
            "CF-Connecting-IP", // Cloudflare
            "True-Client-IP",   // Cloudflare Enterprise / Akamai
            "Ali-CDN-Real-IP",  // 阿里云 CDN
            "X-Edge-Real-IP",   // 腾讯云 EdgeOne
            "Fastly-Client-IP", // Fastly
            "X-Client-IP",      // 通用 CDN / 负载均衡
            "X-Azure-ClientIP", // Azure Front Door
            "CloudFront-Viewer-Address"); // AWS CloudFront（形如 1.2.3.4:5678 / [::1]:5678）

    private static final String AUTO = "auto";
    private static final String DIRECT = "direct";
    private static final String XFF = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";

    /** 解析可信客户端 IP；任何需要定位客户端来源的地方都应调用本方法。 */
    public static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        ConfigManager config = Main.getConfigManager();
        String trustedHeader = config == null ? AUTO : config.getTrustedClientIpHeader();
        List<String> extraHeaders = config == null ? List.of() : config.getExtraClientIpHeaders();
        return resolve(request.getRemoteAddr(), trustedHeader, request::getHeader, extraHeaders);
    }

    /** 与 {@link #clientIp(HttpServletRequest)} 等价的纯函数版本，便于测试。 */
    static String resolve(String peerAddress, String trustedHeader, Function<String, String> headers) {
        return resolve(peerAddress, trustedHeader, headers, List.of());
    }

    /** 带「自动模式下额外优先尝试的自定义头」的纯函数版本，便于测试。 */
    static String resolve(String peerAddress, String trustedHeader, Function<String, String> headers,
                          List<String> extraHeaders) {
        Function<String, String> source = headers;
        if (source == null) {
            source = name -> null;
        }
        String peer = normalize(peerAddress);
        String header = trustedHeader == null ? "" : trustedHeader.trim();

        if (header.isEmpty() || DIRECT.equalsIgnoreCase(header)) {
            // 纯直连：忽略一切转发头，直接用对端 socket 地址，杜绝伪造。
            return peer;
        }
        if (AUTO.equalsIgnoreCase(header)) {
            return autoDetect(peer, source, extraHeaders);
        }

        // 显式指定头：取指定头部；X-Forwarded-For 为多值链，取最右一格（离我们最近的代理，最不可伪造）
        String raw = source.apply(header);
        String ip = XFF.equalsIgnoreCase(header) ? rightmostEntry(raw) : singleEntry(raw);
        if (ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = peer;
        }
        return normalize(ip);
    }

    /**
     * 依次尝试「配置里额外登记的头」与各家 CDN 的专用头，再退到 {@code X-Forwarded-For} /
     * {@code X-Real-IP} / socket 对端。
     */
    private static String autoDetect(String peer, Function<String, String> headers, List<String> extraHeaders) {
        // 只有在确实经过本机 / 内网反向代理时才信任转发头；公网直连一律用对端地址。
        if (!isLocalPeer(peer)) {
            return peer;
        }
        // 额外登记的头优先：换了新 CDN 只要在配置里加一行，不用改代码。
        if (extraHeaders != null) {
            for (String name : extraHeaders) {
                String header = name == null ? "" : name.trim();
                if (header.isEmpty()) {
                    continue;
                }
                String ip = singleEntry(headers.apply(header));
                if (!ip.isEmpty()) {
                    return ip;
                }
            }
        }
        for (String name : AUTO_CDN_HEADERS) {
            String ip = singleEntry(headers.apply(name));
            if (!ip.isEmpty()) {
                return ip;
            }
        }
        // Nginx 的 proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for 会把回源地址追加到链尾，
        // 所以先摘掉与 X-Real-IP 相同的那一格，剩下的最右一格才是 CDN 写入的真实客户端。
        String fromChain = chainEntry(headers.apply(XFF), headers.apply(X_REAL_IP));
        if (!fromChain.isEmpty()) {
            return fromChain;
        }
        String real = singleEntry(headers.apply(X_REAL_IP));
        return real.isEmpty() ? peer : real;
    }

    /**
     * 从 X-Forwarded-For 链里挑真实客户端：先去掉我们自己这一跳反向代理追加的地址
     * （等于 Nginx 回源时写入的 {@code X-Real-IP}），再从右往左取第一格真实地址。
     */
    static String chainEntry(String rawXff, String upstreamRealIp) {
        if (rawXff == null || rawXff.isBlank()) {
            return "";
        }
        List<String> chain = new ArrayList<>();
        for (String part : rawXff.split(",")) {
            String value = normalize(part);
            if (!value.isEmpty()) {
                chain.add(value);
            }
        }
        // 只摘掉一格：Nginx 的 $proxy_add_x_forwarded_for 只追加当前这一跳；
        // 若 Nginx 已启用 real_ip 模块，X-Real-IP 与链尾都会等于真实客户端，多摘会把客户端本身摘掉。
        String hop = normalize(upstreamRealIp);
        if (!chain.isEmpty() && !hop.isEmpty() && hop.equals(chain.get(chain.size() - 1))) {
            chain.remove(chain.size() - 1);
        }
        for (int i = chain.size() - 1; i >= 0; i--) {
            if (!isLoopbackOrAny(chain.get(i))) {
                return chain.get(i);
            }
        }
        return "";
    }

    /** socket 对端是否本机 / 内网（说明请求确实经过我们自己的反向代理）。 */
    static boolean isLocalPeer(String ip) {
        if (ip == null || ip.isBlank()) {
            // 取不到对端时按本机处理，保持既有部署行为
            return true;
        }
        try {
            InetAddress address = InetAddress.getByName(ip);
            if (address.isLoopbackAddress() || address.isAnyLocalAddress()
                    || address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
                return true;
            }
            // IPv6 唯一本地地址 fc00::/7（Java 不把 ULA 算作 site-local）
            byte[] bytes = address.getAddress();
            return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean isLoopbackOrAny(String ip) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            return address.isLoopbackAddress() || address.isAnyLocalAddress();
        } catch (Exception e) {
            return true;
        }
    }

    /** 取逗号分隔列表的最后一个条目并去掉空白（X-Forwarded-For 场景）。 */
    private static String rightmostEntry(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String[] parts = raw.split(",");
        String last = parts[parts.length - 1];
        return last == null ? "" : last.trim();
    }

    /** 单值头直接取第一个（一般无逗号；有逗号时取最左，因为该头本应是单个 IP）。 */
    private static String singleEntry(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        int comma = raw.indexOf(',');
        String entry = comma < 0 ? raw : raw.substring(0, comma);
        return normalize(entry);
    }

    /** 规范化 IP：去端口、IPv6 方括号、IPv4-mapped 前缀等。 */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String ip = raw.trim();
        if (ip.isEmpty()) {
            return "";
        }
        int comma = ip.indexOf(',');
        if (comma >= 0) {
            ip = ip.substring(0, comma).trim();
        }
        if (ip.startsWith("[")) {
            int end = ip.indexOf(']');
            if (end > 0) {
                ip = ip.substring(1, end);
            }
        }
        if (ip.startsWith("::ffff:")) {
            ip = ip.substring("::ffff:".length());
        }
        if (ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            return "";
        }
        // IPv4 带端口 1.2.3.4:5678
        int firstColon = ip.indexOf(':');
        if (firstColon > 0 && ip.indexOf('.') > 0 && firstColon == ip.lastIndexOf(':')
                && ip.substring(firstColon + 1).chars().allMatch(Character::isDigit)) {
            ip = ip.substring(0, firstColon);
        }
        return isPlausible(ip) ? ip : "";
    }

    private static boolean isPlausible(String ip) {
        if (ip.contains(":")) {
            return true;
        }
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            try {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }
}
