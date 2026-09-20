package com.neko.music.handlers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicDetailPageHandlerTest {

    @Test
    void normalBrowsersReceiveSpaShell() {
        assertFalse(MusicDetailPageHandler.shouldRenderSeo(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"));
        assertFalse(MusicDetailPageHandler.shouldRenderSeo(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) "
                        + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"));
    }

    @Test
    void crawlersAndNonBrowserClientsReceiveSeoHtml() {
        assertTrue(MusicDetailPageHandler.shouldRenderSeo(
                "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"));
        assertTrue(MusicDetailPageHandler.shouldRenderSeo(
                "facebookexternalhit/1.1"));
        // QQ 分享预览抓取器自带 Mozilla 前缀，靠 UA 关键词识别
        assertTrue(MusicDetailPageHandler.shouldRenderSeo("QQShareProxy/1.0"));
        assertTrue(MusicDetailPageHandler.shouldRenderSeo(
                "Mozilla/5.0 (Linux; U; Android 10; zh-cn) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Version/4.0 Chrome/66.0.3359.126 "
                        + "MQQBrowser/6.2 TBS/044904 QQShareProxy/7.9.9"));
        assertTrue(MusicDetailPageHandler.shouldRenderSeo("curl/8.7.1"));
        assertTrue(MusicDetailPageHandler.shouldRenderSeo(""));
        assertTrue(MusicDetailPageHandler.shouldRenderSeo(null));
    }
}
