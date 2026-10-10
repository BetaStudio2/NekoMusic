package com.neko.music.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 出站地址校验：协议、主机名形态、官方域名白名单与内网/保留地址判定。 */
class OutboundUrlGuardTest {

    private static final Set<String> KUGOU_HOSTS = Set.of("kugou.com");

    @Test
    @DisplayName("非 http(s) 协议、带用户信息、缺主机名一律拒绝")
    void rejectsNonHttpOrMalformedUrls() {
        assertBlocked("file:///etc/passwd");
        assertBlocked("ftp://93.184.216.34/x");
        assertBlocked("gopher://93.184.216.34/");
        assertBlocked("jar:http://example.com/a.jar!/b");
        assertBlocked("//example.com/x");
        assertBlocked("http://user:pass@example.com/");
        assertBlocked("http://example.com@127.0.0.1/");
        assertBlocked("http:///no-host");
    }

    @Test
    @DisplayName("内网、回环、链路本地、云元数据、保留地址全部拒绝")
    void rejectsInternalAndReservedAddresses() {
        assertBlocked("http://127.0.0.1:6379/share/");
        assertBlocked("http://127.1/x");
        assertBlocked("http://10.0.0.1/x");
        assertBlocked("http://172.16.0.1/x");
        assertBlocked("http://192.168.1.1/x");
        assertBlocked("http://169.254.169.254/latest/meta-data/");
        assertBlocked("http://100.100.100.200/latest/meta-data/");
        assertBlocked("http://0.0.0.0/");
        assertBlocked("http://[::1]/");
        assertBlocked("http://[fc00::1]/");
        assertBlocked("http://[fe80::1]/");
        assertBlocked("http://[::ffff:127.0.0.1]/");
        assertBlocked("http://198.18.0.1/");
        assertBlocked("http://203.0.113.9/");
    }

    @Test
    @DisplayName("数字变体写法与内网主机名在解析前就被拒绝")
    void rejectsNumericVariantsAndIntranetNames() {
        assertBlocked("http://2130706433/share/");
        assertBlocked("http://0x7f000001/share/");
        assertBlocked("http://127.0.0.01/share/");
        assertBlocked("http://localhost/share/");
        assertBlocked("http://redis/share/");
        assertBlocked("http://metadata.google.internal/computeMetadata/v1/");
        assertBlocked("http://svc.cluster.local/x");
        assertBlocked("http://kugou.com.evil.com/songlist/");
    }

    @Test
    @DisplayName("官方域名白名单只认本域与子域，不能用后缀拼接绕过")
    void allowlistMatchesDomainAndSubdomainsOnly() {
        assertTrue(OutboundUrlGuard.isAllowedHost("kugou.com", KUGOU_HOSTS));
        assertTrue(OutboundUrlGuard.isAllowedHost("m.kugou.com", KUGOU_HOSTS));
        assertTrue(OutboundUrlGuard.isAllowedHost("www.kugou.com", KUGOU_HOSTS));
        assertTrue(OutboundUrlGuard.isAllowedHost("KUGOU.COM", KUGOU_HOSTS));
        assertFalse(OutboundUrlGuard.isAllowedHost("evilkugou.com", KUGOU_HOSTS));
        assertFalse(OutboundUrlGuard.isAllowedHost("kugou.com.evil.com", KUGOU_HOSTS));
        assertFalse(OutboundUrlGuard.isAllowedHost("127.0.0.1", KUGOU_HOSTS));
        assertFalse(OutboundUrlGuard.isAllowedHost(null, KUGOU_HOSTS));

        assertThrows(OutboundUrlGuard.BlockedUrlException.class,
                () -> OutboundUrlGuard.requireAllowedHttpUrl("http://example.com/kugou.com/songlist/", KUGOU_HOSTS));
    }

    @Test
    @DisplayName("公网地址判定：内网与保留段为 false，公网为 true")
    void classifiesPublicAddressRanges() throws Exception {
        for (String raw : new String[]{"127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.0.1",
                "169.254.169.254", "100.64.0.1", "100.100.100.200", "0.0.0.0", "0.1.2.3",
                "224.0.0.1", "255.255.255.255", "198.18.0.1", "203.0.113.9", "192.0.2.5",
                "192.0.0.9", "198.51.100.7", "192.88.99.1", "::1", "fc00::1", "fd12::1",
                "fe80::1", "2001:db8::1", "::"}) {
            assertFalse(OutboundUrlGuard.isPublicAddress(InetAddress.getByName(raw)), raw);
        }
        for (String raw : new String[]{"93.184.216.34", "1.1.1.1", "8.8.8.8",
                "172.32.0.1", "2606:4700:4700::1111", "2404:6800:4008::200e"}) {
            assertTrue(OutboundUrlGuard.isPublicAddress(InetAddress.getByName(raw)), raw);
        }
    }

    @Test
    @DisplayName("解析结果里只要有一条内网地址就整体拒绝，避免解析被指向内网")
    void rejectsWhenAnyResolvedAddressIsInternal() throws Exception {
        assertThrows(OutboundUrlGuard.BlockedUrlException.class,
                () -> OutboundUrlGuard.checkPublicAddresses(new InetAddress[]{
                        InetAddress.getByName("93.184.216.34"), InetAddress.getByName("10.0.0.5")}));
        assertThrows(OutboundUrlGuard.BlockedUrlException.class,
                () -> OutboundUrlGuard.checkPublicAddresses(new InetAddress[0]));
    }

    @Test
    @DisplayName("主机名规范化：去方括号与尾点、拒绝非法字符")
    void normalizesHostNames() throws Exception {
        assertTrue(OutboundUrlGuard.normalizeHost("[FE80::1]")
                .equals(InetAddress.ofLiteral("fe80::1").getHostAddress()));
        assertTrue(OutboundUrlGuard.normalizeHost("[::ffff:127.0.0.1]").equals("127.0.0.1"));
        assertTrue(OutboundUrlGuard.normalizeHost("M.Kugou.com.").equals("m.kugou.com"));
        assertThrows(OutboundUrlGuard.BlockedUrlException.class, () -> OutboundUrlGuard.normalizeHost("a b.com"));
        assertThrows(OutboundUrlGuard.BlockedUrlException.class, () -> OutboundUrlGuard.normalizeHost("host/x"));
        assertThrows(OutboundUrlGuard.BlockedUrlException.class, () -> OutboundUrlGuard.normalizeHost(""));
    }

    private static void assertBlocked(String url) {
        assertThrows(OutboundUrlGuard.BlockedUrlException.class,
                () -> OutboundUrlGuard.requirePublicHttpUrl(url), url);
    }

    @Test
    @DisplayName("放行公网地址时返回值可直接交给 HTTP 客户端")
    void returnsUriForPublicTarget() throws Exception {
        assertTrue(OutboundUrlGuard.requirePublicHttpUrl("http://93.184.216.34/a").toString()
                .equals("http://93.184.216.34/a"));
        IOException failure = assertThrows(OutboundUrlGuard.BlockedUrlException.class,
                () -> OutboundUrlGuard.requirePublicHttpUrl("http://93.184.216.34:80/a b"));
        assertTrue(failure.getMessage() != null);
    }

    @Test
    @DisplayName("公网地址也不放行：不在该客户端上游域名白名单内就拒绝")
    void rejectsPublicHostOutsideAllowlist() {
        assertThrows(OutboundUrlGuard.BlockedUrlException.class,
                () -> OutboundUrlGuard.requireAllowedTarget(
                        java.net.URI.create("http://93.184.216.34/x"), KUGOU_HOSTS));
        assertThrows(OutboundUrlGuard.BlockedUrlException.class,
                () -> OutboundUrlGuard.requireAllowedTarget(java.net.URI.create("http://example.com/x"), KUGOU_HOSTS));
        assertThrows(OutboundUrlGuard.BlockedUrlException.class,
                () -> OutboundUrlGuard.requireAllowedTarget(null, KUGOU_HOSTS));
        assertThrows(IllegalArgumentException.class,
                () -> OutboundUrlGuard.requireAllowedTarget(
                        java.net.URI.create("http://93.184.216.34/x"), Set.of()));
    }
}
