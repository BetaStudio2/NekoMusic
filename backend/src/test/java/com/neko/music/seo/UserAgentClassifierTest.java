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
    void letsBrowsersAndNativeClientsThroughApi() {
        // 浏览器
        assertFalse(UserAgentClassifier.isBotForApi(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"));
        assertFalse(UserAgentClassifier.isBotForApi(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1"));
        assertFalse(UserAgentClassifier.isBotForApi(
                "Mozilla/5.0 (Linux; Android 13; 22081212C) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36 MicroMessenger/8.0.49"));
        // 原生客户端 / 播放器（虽然不含 Mozilla）
        assertFalse(UserAgentClassifier.isBotForApi("okhttp/4.12.0"));
        assertFalse(UserAgentClassifier.isBotForApi("Dalvik/2.1.0 (Linux; U; Android 13; Pixel 7)"));
        assertFalse(UserAgentClassifier.isBotForApi("libmpv/0.36"));
        assertFalse(UserAgentClassifier.isBotForApi("VLC/3.0.20 LibVLC/3.0.20"));
        assertFalse(UserAgentClassifier.isBotForApi("FFmpeg/6.1 libavformat/60.16.100"));
        assertFalse(UserAgentClassifier.isBotForApi(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) NekoMusicPC/1.0 QtWebEngine/6.6.0"));
        // 空 UA：保守放行，交由限流兜底
        assertFalse(UserAgentClassifier.isBotForApi(null));
        assertFalse(UserAgentClassifier.isBotForApi(""));
        assertFalse(UserAgentClassifier.isBotForApi("   "));
    }
}
