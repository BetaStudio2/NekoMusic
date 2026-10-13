package com.neko.music.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 访问日志的客户端指纹：类型判定直接决定安全分析能不能把「业务爬虫 / 攻击扫描器 / 官方客户端 /
 * 真实浏览器」分开，口径一旦漂移整张日志表就失去意义，因此在这里锁定。
 */
class ClientFingerprintTest {

    private static final String CHROME_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0.0.0 Safari/537.36";

    @Test
    @DisplayName("类型：官方客户端 / 扫描器 / 爬虫 / 浏览器 / 其它 / 空")
    void classifiesType() {
        assertEquals(ClientFingerprint.OFFICIAL_CLIENT, ClientFingerprint.type("NekoMusic-PC/2026.108.52"));
        assertEquals(ClientFingerprint.SCANNER, ClientFingerprint.type("sqlmap/1.7.2#stable"));
        assertEquals(ClientFingerprint.BOT, ClientFingerprint.type("curl/8.0.1"));
        assertEquals(ClientFingerprint.BROWSER, ClientFingerprint.type(CHROME_UA));
        assertEquals(ClientFingerprint.THIRD_PARTY, ClientFingerprint.type("MyCustomAgent/1.0"));
        assertEquals(ClientFingerprint.UNKNOWN, ClientFingerprint.type(""));
        assertEquals(ClientFingerprint.UNKNOWN, ClientFingerprint.type(null));
    }

    @Test
    @DisplayName("平台与版本：官方 UA 优先，其次 X-Neko-Client，浏览器记 web")
    void resolvesPlatformAndVersion() {
        assertEquals("pc", ClientFingerprint.platform("NekoMusic-PC/2026.108.52", null));
        assertEquals("2026.108.52", ClientFingerprint.version("NekoMusic-PC/2026.108.52", null));

        assertEquals("android", ClientFingerprint.platform(CHROME_UA, "android+202601008"));
        assertEquals("202601008", ClientFingerprint.version(CHROME_UA, "android+202601008"));

        assertEquals("web", ClientFingerprint.platform(CHROME_UA, null));
        assertNull(ClientFingerprint.version(CHROME_UA, null));
    }

    @Test
    @DisplayName("X-Neko-Client 格式不合法时忽略，不把可疑内容写进日志")
    void ignoresMalformedNekoClientHeader() {
        assertNull(ClientFingerprint.parseNekoClient("android"));
        assertNull(ClientFingerprint.parseNekoClient("+202601008"));
        assertNull(ClientFingerprint.parseNekoClient("android+"));
        assertNull(ClientFingerprint.parseNekoClient("android+202601008; rm -rf /"));
        assertNull(ClientFingerprint.parseNekoClient("x".repeat(80) + "+1"));
        // 头不合法时不采信：浏览器的 UA 仍然把它归到 web
        assertEquals("web", ClientFingerprint.platform(CHROME_UA, "android"));
        assertNull(ClientFingerprint.version(CHROME_UA, "android"));
    }

    @Test
    @DisplayName("同网段聚合：IPv4 取 /24，IPv6 取 /48")
    void computesSubnet() {
        assertEquals("1.2.3.0/24", ClientFingerprint.ipSubnet("1.2.3.4"));
        assertEquals("2001:db8:1234::/48", ClientFingerprint.ipSubnet("2001:db8:1234:5678::1"));
        assertNull(ClientFingerprint.ipSubnet(""));
        assertNull(ClientFingerprint.ipSubnet("1.2.3"));
    }
}
