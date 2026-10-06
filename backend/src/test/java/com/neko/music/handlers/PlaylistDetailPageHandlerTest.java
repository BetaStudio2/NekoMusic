package com.neko.music.handlers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaylistDetailPageHandlerTest {

    @Test
    void normalBrowsersReceiveSpaShell() {
        assertFalse(PlaylistDetailPageHandler.shouldRenderSeoByUserAgent(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"));
    }

    @Test
    void crawlersAndNonBrowserClientsReceiveSeoHtml() {
        assertTrue(PlaylistDetailPageHandler.shouldRenderSeoByUserAgent(
                "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"));
        assertTrue(PlaylistDetailPageHandler.shouldRenderSeoByUserAgent("curl/8.7.1"));
        assertTrue(PlaylistDetailPageHandler.shouldRenderSeoByUserAgent("ChatGPT-User/1.0"));
        assertTrue(PlaylistDetailPageHandler.shouldRenderSeoByUserAgent(null));
    }
}
