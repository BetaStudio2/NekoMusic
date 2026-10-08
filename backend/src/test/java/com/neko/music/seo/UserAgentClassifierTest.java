package com.neko.music.seo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 爬虫 / 链接预览 UA 识别与真实浏览器放行的回归测试。 */
class UserAgentClassifierTest {

    @Test
    void treatsQqRichPreviewAsCrawler() {
        assertTrue(UserAgentClassifier.shouldRenderSeo("QQUrlRichService/1.0"));
        assertTrue(UserAgentClassifier.shouldRenderSeo("Mozilla/5.0 (compatible; QQShareProxy/1.0)"));
        assertTrue(UserAgentClassifier.shouldRenderSeo("Mozilla/5.0 (compatible; QQUrlRichService/1.0)"));
    }

    @Test
    void treatsSearchEngineAndPreviewBotsAsCrawlers() {
        assertTrue(UserAgentClassifier.shouldRenderSeo(
                "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"));
        assertTrue(UserAgentClassifier.shouldRenderSeo(
                "Mozilla/5.0 (compatible; bingbot/2.0; +http://www.bing.com/bingbot.htm)"));
        assertTrue(UserAgentClassifier.shouldRenderSeo(
                "Mozilla/5.0 (compatible; Baiduspider/2.0; +http://www.baidu.com/search/spider.html)"));
        assertTrue(UserAgentClassifier.shouldRenderSeo("Sogou web spider/4.0(+http://www.sogou.com/docs/help/webmasters.htm#07)"));
        assertTrue(UserAgentClassifier.shouldRenderSeo("facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"));
        assertTrue(UserAgentClassifier.shouldRenderSeo(
                "Mozilla/5.0 (compatible; Twitterbot/1.0)"));
        assertTrue(UserAgentClassifier.shouldRenderSeo(
                "Mozilla/5.0 (compatible; Discordbot/2.0; +https://discordapp.com)"));
        assertTrue(UserAgentClassifier.shouldRenderSeo("TelegramBot (like TwitterBot)"));
    }

    @Test
    void treatsAiAndSeoCrawlersAsCrawlers() {
        assertTrue(UserAgentClassifier.shouldRenderSeo("Mozilla/5.0 AppleWebKit/537.36 (KHTML, like Gecko); compatible; GPTBot/1.2; +https://openai.com/gptbot"));
        assertTrue(UserAgentClassifier.shouldRenderSeo("Mozilla/5.0 (compatible; OAI-SearchBot/1.0; +https://openai.com/searchbot)"));
        assertTrue(UserAgentClassifier.shouldRenderSeo("Mozilla/5.0 (compatible; ChatGPT-User/1.0; +https://openai.com/bot)"));
        assertTrue(UserAgentClassifier.shouldRenderSeo("Mozilla/5.0 (compatible; PerplexityBot/1.0; +https://perplexity.ai/perplexitybot)"));
        assertTrue(UserAgentClassifier.shouldRenderSeo("Mozilla/5.0 (compatible; ClaudeBot/1.0; +claudebot@anthropic.com)"));
        assertTrue(UserAgentClassifier.shouldRenderSeo("CCBot/2.0 (https://commoncrawl.org/faq/)"));
        assertTrue(UserAgentClassifier.shouldRenderSeo("Mozilla/5.0 (compatible; SemrushBot/7~bl; +http://www.semrush.com/bot.html)"));
        assertTrue(UserAgentClassifier.shouldRenderSeo("Mozilla/5.0 (compatible; AhrefsBot/7.0; +http://ahrefs.com/robot/)"));
    }

    @Test
    void treatsClientsAndHeadlessAsCrawlers() {
        assertTrue(UserAgentClassifier.shouldRenderSeo("curl/8.5.0"));
        assertTrue(UserAgentClassifier.shouldRenderSeo("Wget/1.21.3"));
        assertTrue(UserAgentClassifier.shouldRenderSeo("python-requests/2.31.0"));
        assertTrue(UserAgentClassifier.shouldRenderSeo("Apache-HttpClient/4.5.13 (Java/1.8.0_312)"));
        assertTrue(UserAgentClassifier.shouldRenderSeo("okhttp/4.12.0"));
        assertTrue(UserAgentClassifier.shouldRenderSeo("Go-http-client/1.1"));
        assertTrue(UserAgentClassifier.shouldRenderSeo(
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/120.0.0.0 Safari/537.36"));
        assertTrue(UserAgentClassifier.shouldRenderSeo("Mozilla/5.0 (compatible; UptimeRobot/2.0)"));
        assertTrue(UserAgentClassifier.shouldRenderSeo("Mozilla/5.0 (compatible; ia_archiver/1.0)"));
    }

    @Test
    void treatsEmptyAndNonMozillaAsCrawlers() {
        assertTrue(UserAgentClassifier.shouldRenderSeo(null));
        assertTrue(UserAgentClassifier.shouldRenderSeo(""));
        assertTrue(UserAgentClassifier.shouldRenderSeo("   "));
        assertTrue(UserAgentClassifier.shouldRenderSeo("Dalvik/2.1.0 (Linux; U; Android 13; Pixel 7 Build/TQ3A.230805.001)"));
    }

    @Test
    void letsRealBrowsersThrough() {
        assertFalse(UserAgentClassifier.shouldRenderSeo(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"));
        assertFalse(UserAgentClassifier.shouldRenderSeo(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15"));
        assertFalse(UserAgentClassifier.shouldRenderSeo(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 Edg/124.0.2478.67"));
        assertFalse(UserAgentClassifier.shouldRenderSeo(
                "Mozilla/5.0 (X11; Linux x86_64; rv:126.0) Gecko/20100101 Firefox/126.0"));
        assertFalse(UserAgentClassifier.shouldRenderSeo(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1"));
    }

    @Test
    void keepsInAppBrowsersAndDesktopShellsOnSpa() {
        assertFalse(UserAgentClassifier.shouldRenderSeo(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 MicroMessenger/8.0.49(0x1800312b) NetType/WIFI Language/zh_CN"));
        assertFalse(UserAgentClassifier.shouldRenderSeo(
                "Mozilla/5.0 (Linux; Android 13; 22081212C) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36 MQQBrowser/13.5 TBS/045913"));
        assertFalse(UserAgentClassifier.shouldRenderSeo(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Slack/4.36.140 Chrome/120.0.6099.291 Electron/28.2.4 Safari/537.36"));
    }

    // ---- isBotForApi（/api 防爬）----

    @Test
    void flagsScriptTrafficOnApi() {
        assertTrue(UserAgentClassifier.isBotForApi("curl/8.5.0"));
        assertTrue(UserAgentClassifier.isBotForApi("Wget/1.21.3"));
        assertTrue(UserAgentClassifier.isBotForApi("python-requests/2.31.0"));
        assertTrue(UserAgentClassifier.isBotForApi("Go-http-client/1.1"));
        assertTrue(UserAgentClassifier.isBotForApi(
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/120.0.0.0 Safari/537.36"));
        assertTrue(UserAgentClassifier.isBotForApi(
                "Mozilla/5.0 AppleWebKit/537.36 (KHTML, like Gecko); compatible; GPTBot/1.2"));
        assertTrue(UserAgentClassifier.isBotForApi(
                "Mozilla/5.0 (compatible; AhrefsBot/7.0; +http://ahrefs.com/robot/)"));
        assertTrue(UserAgentClassifier.isBotForApi("Mozilla/5.0 (compatible; UptimeRobot/2.0)"));
    }

    @Test
    void flagsSecurityScannersOnApi() {
        assertTrue(UserAgentClassifier.isBotForApi("sqlmap/1.7.2#stable (https://sqlmap.org)"));
        assertTrue(UserAgentClassifier.isBotForApi("Mozilla/5.00 (Nikto/2.5.0) (Evasions:None)"));
        assertTrue(UserAgentClassifier.isBotForApi(
                "Mozilla/5.0 (compatible; Nmap Scripting Engine; https://nmap.org/book/nse.html)"));
        assertTrue(UserAgentClassifier.isBotForApi("Mozilla/5.0 zgrab/0.x"));
        assertTrue(UserAgentClassifier.isBotForApi("masscan/1.3"));
        assertTrue(UserAgentClassifier.isBotForApi("WPScan v3.8.25 (https://wpscan.com/wordpress-security-scanner)"));
        assertTrue(UserAgentClassifier.isBotForApi("gobuster/3.6"));
        assertTrue(UserAgentClassifier.isBotForApi("Mozilla/5.0 (Nuclei - Open-source project)"));
    }

    @Test
    void browserStructureCheckSeparatesRealBrowsersFromSpoofs() {
        // 真浏览器
        assertTrue(UserAgentClassifier.looksLikeRealBrowser(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"));
        assertTrue(UserAgentClassifier.looksLikeRealBrowser(
                "Mozilla/5.0 (X11; Linux x86_64; rv:126.0) Gecko/20100101 Firefox/126.0"));
        assertTrue(UserAgentClassifier.looksLikeRealBrowser(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1"));
        // iOS WKWebView（微信等）常省略 Safari/Version，仍应识别为浏览器
        assertTrue(UserAgentClassifier.looksLikeRealBrowser(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 MicroMessenger/8.0.40"));
        // 未知/小众爬虫、残缺或仅伪造 Mozilla 前缀
        assertFalse(UserAgentClassifier.looksLikeRealBrowser("MyCollector/1.0"));
        assertFalse(UserAgentClassifier.looksLikeRealBrowser("AcmeIndex/1.0"));
        assertFalse(UserAgentClassifier.looksLikeRealBrowser("Mozilla/5.0"));
        assertFalse(UserAgentClassifier.looksLikeRealBrowser("Mozilla/5.0 (X11; Linux x86_64)"));
        assertFalse(UserAgentClassifier.looksLikeRealBrowser("Mozilla/5.0 (compatible; AcmeIndex/1.0)"));
        assertFalse(UserAgentClassifier.looksLikeRealBrowser("Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1)"));
        assertFalse(UserAgentClassifier.looksLikeRealBrowser(null));
        assertFalse(UserAgentClassifier.looksLikeRealBrowser(""));
    }

    @Test
    void letsBrowsersAndNativeClientsThroughApi() {
        // 浏览器
        assertFalse(UserAgentClassifier.isBotForApi(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"));
        assertFalse(UserAgentClassifier.isBotForApi(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1"));
        assertFalse(UserAgentClassifier.isBotForApi(
                "Mozilla/5.0 (Linux; Android 13; 22081212C) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36 MicroMessenger/8.0.49"));
        // 官方客户端：UA 必须严格是 NekoMusic-<平台>/<版本>（PC 旧版封面 UA 为 NekoMusic Qt）
        assertFalse(UserAgentClassifier.isBotForApi("NekoMusic-android/202601008"));
        assertFalse(UserAgentClassifier.isBotForApi("NekoMusic-PC/1.0"));
        assertFalse(UserAgentClassifier.isBotForApi("NekoMusic Qt"));
        assertFalse(UserAgentClassifier.isNativeClient("NekoMusic Qt"));
        // 播放器 / 命令行工具只取 /media/* 直链，不再豁免 /api：这些 UA 一律按黑名单拦截
        assertTrue(UserAgentClassifier.isBotForApi("okhttp/4.12.0"));
        assertTrue(UserAgentClassifier.isBotForApi("Dalvik/2.1.0 (Linux; U; Android 13; Pixel 7)"));
        assertTrue(UserAgentClassifier.isBotForApi("libmpv/0.36"));
        assertTrue(UserAgentClassifier.isBotForApi("VLC/3.0.20 LibVLC/3.0.20"));
        // 空 UA：一律当爬虫，不能靠「不带 UA」绕过校验
        assertTrue(UserAgentClassifier.isBotForApi(null));
        assertTrue(UserAgentClassifier.isBotForApi(""));
        assertTrue(UserAgentClassifier.isBotForApi("   "));
    }

    @Test
    void doesNotExemptNativeKeywordsEmbeddedInSpoofedUserAgents() {
        // 回归：UA 里出现 android / okhttp / dalvik / qt 等关键词不再构成放行理由，
        // 否则 `sqlmap android` 这类组合能同时绕过黑名单与浏览器完整性两层判定。
        assertTrue(UserAgentClassifier.isBotForApi("sqlmap android"));
        assertTrue(UserAgentClassifier.isBotForApi("python-requests/2.31.0 Android"));
        assertTrue(UserAgentClassifier.isBotForApi("curl/8.5.0 dalvik"));
        assertTrue(UserAgentClassifier.isBotForApi("nikto okhttp"));

        assertFalse(UserAgentClassifier.isNativeClient("android"));
        assertFalse(UserAgentClassifier.isNativeClient("okhttp/4.12.0"));
        assertFalse(UserAgentClassifier.isNativeClient("nekomusic"));
        assertFalse(UserAgentClassifier.isNativeClient("NekoMusic-android/202601008 sqlmap"));
        assertFalse(UserAgentClassifier.isNativeClient(
                "Mozilla/5.0 (Linux; Android 13; 22081212C) AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/116.0.0.0 Mobile Safari/537.36"));

        assertTrue(UserAgentClassifier.isNativeClient("NekoMusic-android/202601008"));
        assertTrue(UserAgentClassifier.isNativeClient("NekoMusic-PC/1.0"));
        assertTrue(UserAgentClassifier.isNativeClient("nekomusic-android/1.0.0"));
        // 官方 UA 必须带平台与版本：裸前缀 / 空格写法 / 非数字版本都不算
        assertFalse(UserAgentClassifier.isNativeClient("NekoMusic-android"));
        assertFalse(UserAgentClassifier.isNativeClient("NekoMusic Android"));
        assertFalse(UserAgentClassifier.isNativeClient("NekoMusic-PC"));
        assertFalse(UserAgentClassifier.isNativeClient("NekoMusic-android/abc"));
        assertFalse(UserAgentClassifier.isNativeClient("NekoMusic-android/202601008/extra"));
    }

    @Test
    void rendersSeoWhenBrowserUserAgentLacksBrowserHeaders() {
        String chrome = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
        // 真浏览器（UA 结构 + 特征头）→ SPA
        assertFalse(UserAgentClassifier.shouldRenderSeo(chrome, true));
        // 伪造浏览器 UA 但无特征头 → SEO
        assertTrue(UserAgentClassifier.shouldRenderSeo(chrome, false));
        // 已知爬虫无论有无特征头都走 SEO
        assertTrue(UserAgentClassifier.shouldRenderSeo("curl/8.5.0", true));
        assertTrue(UserAgentClassifier.shouldRenderSeo("curl/8.5.0", false));
        // 空 UA 走 SEO
        assertTrue(UserAgentClassifier.shouldRenderSeo(null, false));
        assertTrue(UserAgentClassifier.shouldRenderSeo("", true));
    }
}
