package com.neko.music.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * 出站请求地址校验：凡是把「用户输入」或「上游响应」里的地址交给 HTTP 客户端之前，
 * 都必须先过这里，避免服务端被借去访问内网 / 本机 / 云元数据地址（SSRF）。
 *
 * <p>校验项：协议仅限 {@code http} / {@code https}、禁止携带用户信息、主机名不得是内网名或
 * 数字变体写法，并且解析出的全部地址都必须是公网地址。只要有一条解析结果落在内网 / 保留地址，
 * 就整体拒绝，避免域名解析被指向内网。</p>
 *
 * <p>放行后的调用方必须关闭 HTTP 客户端的自动重定向，并对每个 3xx 目标重新调用本类校验，
 * 否则合法站点可以通过重定向把请求引到内网。</p>
 */
public final class OutboundUrlGuard {

    /** 校验不通过时抛出。消息面向用户，不包含解析结果或内网探测信息。 */
    public static class BlockedUrlException extends IOException {
        public BlockedUrlException(String message) {
            super(message);
        }
    }

    private static final int MAX_URL_LENGTH = 2048;

    /** 内网 / 保留后缀：命中即视为内网主机名。 */
    private static final Set<String> BLOCKED_HOST_SUFFIXES = Set.of(
            "localhost", "local", "localdomain", "internal", "intranet", "lan", "home", "home.arpa",
            "corp", "invalid", "test", "onion");

    private OutboundUrlGuard() {
    }

    /** 只要求是公网 http(s) 地址（用于上游响应里带回来的媒体地址等）。 */
    public static URI requirePublicHttpUrl(String rawUrl) throws IOException {
        return validate(rawUrl, null);
    }

    /** 要求是公网 http(s) 地址，且主机属于给定的官方域名白名单（精确匹配或子域）。 */
    public static URI requireAllowedHttpUrl(String rawUrl, Set<String> allowedHostSuffixes)
            throws IOException {
        if (allowedHostSuffixes == null || allowedHostSuffixes.isEmpty()) {
            throw new IllegalArgumentException("allowedHostSuffixes 不能为空");
        }
        return validate(rawUrl, allowedHostSuffixes);
    }

    /** 解析主机并确认其全部地址都是公网地址。 */
    public static void requirePublicHost(String rawHost) throws IOException {
        String host = normalizeHost(rawHost);
        checkPublicAddresses(resolve(host));
    }

    /** 已解析出来的 {@link URI} 版本：供 HTTP 客户端在真正发包前对请求地址再兜一次底。 */
    public static void requireAllowedTarget(URI uri, Set<String> allowedHostSuffixes) throws IOException {
        if (uri == null) {
            throw new BlockedUrlException("地址为空");
        }
        requireAllowedHttpUrl(uri.toString(), allowedHostSuffixes);
    }

    /** 主机名（或 IP 字面量）是否命中白名单：完全相等或为其子域。 */
    static boolean isAllowedHost(String host, Set<String> allowedHostSuffixes) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        for (String suffix : allowedHostSuffixes) {
            String allowed = suffix.toLowerCase(Locale.ROOT);
            if (normalized.equals(allowed) || normalized.endsWith("." + allowed)) {
                return true;
            }
        }
        return false;
    }

    /** 地址是否属于公网可访问范围（内网、回环、链路本地、保留段一律为 false）。 */
    public static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            int third = bytes[2] & 0xFF;
            if (first == 0) {
                return false;
            }
            if (first == 100 && second >= 64 && second <= 127) {
                return false;
            }
            if (first == 192 && second == 0 && (third == 0 || third == 2)) {
                return false;
            }
            if (first == 192 && second == 88 && third == 99) {
                return false;
            }
            if (first == 198 && (second == 18 || second == 19)) {
                return false;
            }
            if (first == 198 && second == 51 && third == 100) {
                return false;
            }
            if (first == 203 && second == 0 && third == 113) {
                return false;
            }
            return first < 240;
        }
        if (bytes.length == 16) {
            int first = bytes[0] & 0xFF;
            if ((first & 0xFE) == 0xFC) {
                return false;
            }
            boolean docPrefix = first == 0x20 && (bytes[1] & 0xFF) == 0x01
                    && (bytes[2] & 0xFF) == 0x0D && (bytes[3] & 0xFF) == 0xB8;
            return !docPrefix;
        }
        return false;
    }

    /** 解析出的地址里只要有一个不是公网地址就拒绝。 */
    static void checkPublicAddresses(InetAddress[] addresses) throws IOException {
        if (addresses == null || addresses.length == 0) {
            throw new BlockedUrlException("地址无法解析");
        }
        for (InetAddress address : addresses) {
            if (!isPublicAddress(address)) {
                throw new BlockedUrlException("目标地址不可访问");
            }
        }
    }

    private static URI validate(String rawUrl, Set<String> allowedHostSuffixes) throws IOException {
        URI uri = requireHttpUri(rawUrl);
        String host = normalizeHost(uri.getHost());
        if (allowedHostSuffixes != null && !isAllowedHost(host, allowedHostSuffixes)) {
            throw new BlockedUrlException("该地址不属于允许访问的站点");
        }
        checkPublicAddresses(resolve(host));
        return uri;
    }

    private static URI requireHttpUri(String rawUrl) throws IOException {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new BlockedUrlException("地址为空");
        }
        String trimmed = rawUrl.trim();
        if (trimmed.length() > MAX_URL_LENGTH) {
            throw new BlockedUrlException("地址过长");
        }
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            throw new BlockedUrlException("地址格式非法");
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new BlockedUrlException("地址缺少协议");
        }
        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new BlockedUrlException("只支持 http/https 地址");
        }
        if (uri.getUserInfo() != null) {
            throw new BlockedUrlException("地址不允许携带用户信息");
        }
        return uri;
    }

    /** 规范化主机名（去方括号、小写、去 FQDN 尾点），并拒绝内网名与数字变体写法。 */
    static String normalizeHost(String rawHost) throws IOException {
        if (rawHost == null || rawHost.isBlank()) {
            throw new BlockedUrlException("地址缺少主机名");
        }
        String host = rawHost.trim();
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        host = host.toLowerCase(Locale.ROOT);
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        if (host.isEmpty()) {
            throw new BlockedUrlException("地址缺少主机名");
        }
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            boolean valid = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '-' || c == ':';
            if (!valid) {
                throw new BlockedUrlException("地址包含非法的主机名");
            }
        }
        if (host.indexOf(':') >= 0) {
            return parseIpLiteral(host);
        }
        if (isNumericVariantHost(host)) {
            throw new BlockedUrlException("地址不允许使用数字形式的主机名");
        }
        if (host.indexOf('.') < 0) {
            throw new BlockedUrlException("地址不允许使用内网主机名");
        }
        for (String suffix : BLOCKED_HOST_SUFFIXES) {
            if (host.endsWith("." + suffix)) {
                throw new BlockedUrlException("地址不允许指向内网主机名");
            }
        }
        return host;
    }

    /** 只按字面量解析 IPv6（不触发 DNS），并归一化成标准写法。 */
    private static String parseIpLiteral(String host) throws IOException {
        try {
            return InetAddress.ofLiteral(host).getHostAddress();
        } catch (IllegalArgumentException e) {
            throw new BlockedUrlException("地址包含非法的主机名");
        }
    }

    /** 十进制整数、十六进制、带前导零的八进制写法都能代表同一个 IP，统一按非法处理。 */
    private static boolean isNumericVariantHost(String host) {
        if (host.matches("[0-9]+")) {
            return true;
        }
        if (host.contains("0x") || host.contains("0X")) {
            return true;
        }
        int start = 0;
        for (int i = 0; i <= host.length(); i++) {
            if (i == host.length() || host.charAt(i) == '.') {
                String label = host.substring(start, i);
                if (label.length() > 1 && label.charAt(0) == '0') {
                    return true;
                }
                start = i + 1;
            }
        }
        return false;
    }

    private static InetAddress[] resolve(String host) throws IOException {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses == null || addresses.length == 0) {
                throw new BlockedUrlException("地址无法解析");
            }
            return addresses;
        } catch (UnknownHostException e) {
            throw new BlockedUrlException("地址无法解析");
        }
    }
}
