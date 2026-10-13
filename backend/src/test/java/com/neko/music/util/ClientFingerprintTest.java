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
        assertEquals(ClientFingerprint.OFFICIAL_CLIENT, ClientFingerprint.type("NekoMusic-PC/2026.108.52", null));
        assertEquals(ClientFingerprint.SCANNER, ClientFingerprint.type("sqlmap/1.7.2#stable", null));
        assertEquals(ClientFingerprint.BOT, ClientFingerprint.type("curl/8.0.1", null));
        assertEquals(ClientFingerprint.BROWSER, ClientFingerprint.type(CHROME_UA, null));
        assertEquals(ClientFingerprint.THIRD_PARTY, ClientFingerprint.type("MyCustomAgent/1.0", null));
        assertEquals(ClientFingerprint.UNKNOWN, ClientFingerprint.type("", null));
        assertEquals(ClientFingerprint.UNKNOWN, ClientFingerprint.type(null, null));
    }

    @Test
    @DisplayName("类型：UA 看不出身份时由 X-Neko-Client 补上（UA 被中间层改写也要认出自家客户端）")
    void classifiesTypeFromNekoClientHeader() {
        // PC / Android 客户端：UA 被代理或系统 WebView 换成别的写法，自报平台仍能认出来
        assertEquals(ClientFingerprint.OFFICIAL_CLIENT, ClientFingerprint.type("SomeAgent/1.0", "android+202601008"));
        assertEquals(ClientFingerprint.OFFICIAL_CLIENT, ClientFingerprint.type("SomeAgent/1.0", "PC+2026.108.52"));
        // 第三方客户端：登记放行的自报平台不在官方名单里
        assertEquals(ClientFingerprint.THIRD_PARTY, ClientFingerprint.type("SomePlayer/1.0", "foo+1.0"));
        // 浏览器端：web+<版本> 是前端注入的，按浏览器记；UA 已经像浏览器时以 UA 为准
        assertEquals(ClientFingerprint.BROWSER, ClientFingerprint.type(CHROME_UA, "web+1.2.3"));
        assertEquals(ClientFingerprint.BROWSER, ClientFingerprint.type(CHROME_UA, "pc+2026.108.52"));
    }

    @Test
    @DisplayName("类型：自报平台不得越过黑名单，空 UA 也不因自报而豁免")
    void nekoClientHeaderCannotOverrideBlacklist() {
        // 脚本随手伪造一个 X-Neko-Client 不能把自己洗成官方客户端
        assertEquals(ClientFingerprint.BOT, ClientFingerprint.type("okhttp/4.12.0", "android+202601008"));
        assertEquals(ClientFingerprint.BOT, ClientFingerprint.type("curl/8.0.1", "pc+2026.108.52"));
        assertEquals(ClientFingerprint.SCANNER, ClientFingerprint.type("sqlmap/1.7.2#stable", "android+1"));
        // 空 / 缺失 UA 一律按爬虫口径处理，自报平台不构成豁免（后端对 /api/* 直接 403）
        assertEquals(ClientFingerprint.UNKNOWN, ClientFingerprint.type("", "android+202601008"));
        assertEquals(ClientFingerprint.UNKNOWN, ClientFingerprint.type(null, "pc+2026.108.52"));
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
