package com.neko.music.util;

import com.neko.music.Main;
import jakarta.servlet.http.HttpServletRequest;

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
 * <p>策略：只信内部可配置的<b>单一可信头</b>（Nginx 覆盖写入、客户端无法伪造的），忽略其余的转发头。
 * 可信头由 {@code network.trusted_client_ip_header} 指定，默认 {@code X-Real-IP}（对应 Nginx 反代 + CDN 部署）。
 * 置空或 {@code direct} 时（纯直连公网），一律忽略转发头、只信 socket 对端地址 {@code getRemoteAddr()}。</p>
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    /** 解析可信客户端 IP；任何需要定位客户端来源的地方都应调用本方法。 */
    public static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String remote = request.getRemoteAddr();

        String trustedHeader = Main.getConfigManager() == null
                ? "X-Real-IP"
                : Main.getConfigManager().getTrustedClientIpHeader();
        if (trustedHeader == null || trustedHeader.isBlank()
                || "direct".equalsIgnoreCase(trustedHeader)) {
            // 纯直连：忽略一切转发头，直接用对端 socket 地址，杜绝伪造。
            return normalize(remote);
        }

        // 可信代理：取指定头部；X-Forwarded-For 为多值链，取最右一格（离我们最近的代理，最不可伪造）
        String raw = request.getHeader(trustedHeader);
        String ip = "X-Forwarded-For".equalsIgnoreCase(trustedHeader)
                ? rightmostEntry(raw)
                : singleEntry(raw);
        if (ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = remote;
        }
        return normalize(ip);
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
        return entry.trim();
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