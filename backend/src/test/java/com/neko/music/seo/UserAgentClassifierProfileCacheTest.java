package com.neko.music.seo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UA 判定缓存：缓存只是把同一 UA 的判定复用一次，结论必须与逐个方法直接判定完全一致
 * （否则防爬 / SEO 分流 / 访问日志会因为「有没有命中缓存」而给出不同结果）。
 */
class UserAgentClassifierProfileCacheTest {

    private static final String[] SAMPLE_UAS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 27_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/27.0 Mobile/15E148 Safari/604.1",
            "NekoMusic-PC/2026.108.52",
            "NekoMusic-android/202601008",
            "NekoMusic-AWatch/1.4.0",
            "NekoMusic-android",
            "sqlmap/1.7 android",
            "curl/8.5.0",
            "python-requests/2.31.0",
            "Googlebot/2.1 (+http://www.google.com/bot.html)",
            "Via/1.9.0 (iPad; iOS 27.0.1; Scale/2.00)",
            "",
            "   "};

    @BeforeEach
    @AfterEach
    void clearCache() {
        UserAgentClassifier.clearProfileCache();
    }

    @Test
    void cachedResultMatchesDirectDecision() {
        for (String ua : SAMPLE_UAS) {
            UserAgentClassifier.Profile first = UserAgentClassifier.profile(ua);
            UserAgentClassifier.Profile second = UserAgentClassifier.profile(ua);
            assertEquals(first, second, ua);
            assertEquals(first.bot(), UserAgentClassifier.isBotForApi(ua), ua);
            assertEquals(first.browser(), UserAgentClassifier.looksLikeRealBrowser(ua), ua);
            assertEquals(first.scanner(), UserAgentClassifier.isSecurityScanner(ua), ua);
            assertEquals(first.nativeClient(), UserAgentClassifier.isNativeClient(ua), ua);
            assertEquals(first.platform(), UserAgentClassifier.officialClientPlatform(ua), ua);
            assertEquals(first.version(), UserAgentClassifier.officialClientVersion(ua), ua);
        }
    }

    @Test
    void emptyUaIsTreatedAsBot() {
        assertTrue(UserAgentClassifier.profile(null).bot());
        assertTrue(UserAgentClassifier.profile("").bot());
        assertFalse(UserAgentClassifier.profile(null).browser());
    }

    @Test
    void officialClientRequiresVersionAndKnownPlatform() {
        assertTrue(UserAgentClassifier.profile("NekoMusic-PC/2026.108.52").nativeClient());
        assertEquals("pc", UserAgentClassifier.profile("NekoMusic-PC/2026.108.52").platform());
        assertEquals("2026.108.52", UserAgentClassifier.profile("NekoMusic-PC/2026.108.52").version());

        // 缺版本 / 未登记平台一律不算官方客户端
        assertFalse(UserAgentClassifier.profile("NekoMusic-android").nativeClient());
        assertFalse(UserAgentClassifier.profile("NekoMusic-unknown/1.0").nativeClient());
        assertFalse(UserAgentClassifier.profile("NekoMusic-watch/1.4.0").nativeClient());
        assertFalse(UserAgentClassifier.profile("NekoMusic-AndroidWatch/1.4.0").nativeClient());

        assertTrue(UserAgentClassifier.profile("NekoMusic-AWatch/1.4.0").nativeClient());
        assertEquals("awatch", UserAgentClassifier.profile("NekoMusic-AWatch/1.4.0").platform());
        assertEquals("1.4.0", UserAgentClassifier.profile("NekoMusic-AWatch/1.4.0").version());
    }

    @Test
    void cacheIsBounded() {
        // 超过上限时整体清空，内存不随 UA 种类无限增长
        for (int i = 0; i < UserAgentClassifier.PROFILE_CACHE_MAX + 50; i++) {
            UserAgentClassifier.profile("custom-agent/" + i);
        }
        // 清空后仍然给出同样的结论（不残留、不串味）
        assertTrue(UserAgentClassifier.profile("sqlmap/1.7").bot());
        assertTrue(UserAgentClassifier.profile("NekoMusic-PC/2026.108.52").nativeClient());
    }
}
