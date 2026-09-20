package com.neko.music.util;

import com.neko.music.Main;
import jakarta.servlet.http.HttpServletRequest;

/** 从反代请求头或既有业务配置解析对外站点根 URL（无尾斜杠） */
public final class SiteUrlResolver {
    private SiteUrlResolver() {}

    public static String resolvePublicSiteBase(HttpServletRequest request) {
        return Main.getConfigManager().getVideoRenderNotifyFrontendBaseUrl();
    }




}
