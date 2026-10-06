package com.neko.music.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 客户端 IP 解析：显式头 / direct / 多 CDN 自适应（auto）三种模式。 */
class ClientIpResolverTest {

    private static Function<String, String> headers(String... pairs) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(pairs[i].toLowerCase(), pairs[i + 1]);
        }
        return name -> name == null ? null : map.get(name.toLowerCase());
    }

    @Test
    @DisplayName("direct / 空白：忽略所有转发头，只用 socket 对端")
    void directModeIgnoresForwardedHeaders() {
        Function<String, String> h = headers(
                "X-Real-IP", "1.2.3.4",
                "X-Forwarded-For", "5.6.7.8",
                "CF-Connecting-IP", "9.9.9.9");

        assertEquals("203.0.113.7", ClientIpResolver.resolve("203.0.113.7", "direct", h));
        assertEquals("203.0.113.7", ClientIpResolver.resolve("203.0.113.7", "", h));
        assertEquals("203.0.113.7", ClientIpResolver.resolve("203.0.113.7", "   ", h));
    }

    @Test
    @DisplayName("显式头名：只信该头，缺失时退回 socket 对端")
    void explicitHeaderMode() {
        Function<String, String> h = headers(
                "X-Real-IP", "1.2.3.4",
                "X-Forwarded-For", "5.6.7.8, 9.9.9.9");

        assertEquals("1.2.3.4", ClientIpResolver.resolve("127.0.0.1", "X-Real-IP", h));
        assertEquals("9.9.9.9", ClientIpResolver.resolve("127.0.0.1", "X-Forwarded-For", h));
        assertEquals("127.0.0.1", ClientIpResolver.resolve("127.0.0.1", "CF-Connecting-IP", h));
    }

    @Test
    @DisplayName("auto：优先 CDN 专用头")
    void autoPrefersDedicatedCdnHeaders() {
        assertEquals("1.1.1.1", ClientIpResolver.resolve("127.0.0.1", "auto",
                headers("CF-Connecting-IP", "1.1.1.1")));
        assertEquals("2.2.2.2", ClientIpResolver.resolve("127.0.0.1", "auto",
                headers("True-Client-IP", "2.2.2.2")));
        assertEquals("3.3.3.3", ClientIpResolver.resolve("127.0.0.1", "auto",
                headers("Ali-CDN-Real-IP", "3.3.3.3")));
        // 专用头优先于通用头
        assertEquals("1.1.1.1", ClientIpResolver.resolve("127.0.0.1", "auto",
                headers("CF-Connecting-IP", "1.1.1.1", "X-Real-IP", "8.8.8.8")));
    }

    @Test
    @DisplayName("auto：X-Forwarded-For 跳过本机 Nginx 追加的回源地址")
    void autoSkipsOwnProxyHopInForwardedChain() {
        // 真实案例：CDN 写入客户端 IPv6，Nginx 用 $proxy_add_x_forwarded_for 追加回源地址，
        // 并把同一个回源地址写进 X-Real-IP。真实客户端是链尾被摘掉之后剩下的那一格。
        Function<String, String> h = headers(
                "X-Forwarded-For", "2408:820c:4e09:8bd0:eb32:c5f4:d853:b1ce, 101.66.163.34",
                "X-Real-IP", "101.66.163.34");

        assertEquals("2408:820c:4e09:8bd0:eb32:c5f4:d853:b1ce",
                ClientIpResolver.resolve("127.0.0.1", "auto", h));
    }

    @Test
    @DisplayName("auto：CDN 在客户端伪造的链尾追加真实 IP 时取右起第一格")
    void autoTakesRightmostRemainingAfterOwnHop() {
        Function<String, String> h = headers(
                "X-Forwarded-For", "1.2.3.4, 5.6.7.8, 203.0.113.9",
                "X-Real-IP", "203.0.113.9");

        assertEquals("5.6.7.8", ClientIpResolver.resolve("127.0.0.1", "auto", h));
    }

    @Test
    @DisplayName("auto：无 CDN 时退回 X-Real-IP，保持原行为")
    void autoFallsBackToRealIp() {
        Function<String, String> h = headers(
                "X-Forwarded-For", "1.2.3.4",
                "X-Real-IP", "1.2.3.4");

        assertEquals("1.2.3.4", ClientIpResolver.resolve("127.0.0.1", "auto", h));
        assertEquals("127.0.0.1", ClientIpResolver.resolve("127.0.0.1", "auto", headers()));
    }

    @Test
    @DisplayName("auto：配置登记的自定义头优先于内置 CDN 清单（换新 CDN 不用改代码）")
    void autoExtraHeadersTakePriority() {
        Function<String, String> h = headers(
                "X-My-Cdn-Real-IP", "203.0.113.20",
                "CF-Connecting-IP", "1.1.1.1");

        assertEquals("203.0.113.20", ClientIpResolver.resolve(
                "127.0.0.1", "auto", h, java.util.List.of("X-My-Cdn-Real-IP")));
        // 未登记该头时仍走内置清单
        assertEquals("1.1.1.1", ClientIpResolver.resolve(
                "127.0.0.1", "auto", h, java.util.List.of()));
    }

    @Test
    @DisplayName("auto：只摘一格本机回源；Nginx 已启用 real_ip 时不会把真实客户端摘掉")
    void autoStripsOnlyOneOwnHop() {
        // Nginx 启用 real_ip 模块后 $remote_addr 已是客户端：链尾出现两格相同地址，只应摘一格。
        Function<String, String> h = headers(
                "X-Forwarded-For", "9.9.9.9, 198.51.100.7, 198.51.100.7",
                "X-Real-IP", "198.51.100.7");

        assertEquals("198.51.100.7", ClientIpResolver.resolve("127.0.0.1", "auto", h));
    }

    @Test
    @DisplayName("auto：公网直连时不信任转发头，避免归属地被伪造")
    void autoIgnoresForwardedHeadersForPublicPeer() {
        Function<String, String> h = headers(
                "CF-Connecting-IP", "1.1.1.1",
                "X-Forwarded-For", "2.2.2.2",
                "X-Real-IP", "3.3.3.3");

        assertEquals("198.51.100.5", ClientIpResolver.resolve("198.51.100.5", "auto", h));
    }

    @Test
    @DisplayName("auto：内网对端：Docker 网桥 / IPv6 ULA 也算本机反代")
    void localPeerDetection() {
        assertTrue(ClientIpResolver.isLocalPeer("127.0.0.1"));
        assertTrue(ClientIpResolver.isLocalPeer("::1"));
        assertTrue(ClientIpResolver.isLocalPeer("172.17.0.1"));
        assertTrue(ClientIpResolver.isLocalPeer("10.0.0.9"));
        assertTrue(ClientIpResolver.isLocalPeer("192.168.1.2"));
        assertTrue(ClientIpResolver.isLocalPeer("fd00::1"));
        assertFalse(ClientIpResolver.isLocalPeer("101.66.163.34"));
        assertFalse(ClientIpResolver.isLocalPeer("2408:820c:4e09:8bd0:eb32:c5f4:d853:b1ce"));
    }

    @Test
    @DisplayName("normalize：去端口 / 方括号 / IPv4-mapped 前缀，丢弃非法值")
    void normalizeIpLiterals() {
        assertEquals("1.2.3.4", ClientIpResolver.normalize("1.2.3.4:5678"));
        assertEquals("1.2.3.4", ClientIpResolver.normalize("::ffff:1.2.3.4"));
        assertEquals("::1", ClientIpResolver.normalize("[::1]:80"));
        assertEquals("", ClientIpResolver.normalize("unknown"));
        assertEquals("", ClientIpResolver.normalize("1.2.3"));
        assertEquals("", ClientIpResolver.normalize("999.1.1.1"));
        assertEquals("", ClientIpResolver.normalize(null));
    }
}
