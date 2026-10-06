package com.neko.music.seo;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 浏览器特征头的提取与判定（供防爬、SEO 分流、详情页等多处复用，避免逻辑漂移）。
 *
 * <p>真实浏览器访问页面或 fetch 接口时，至少会带 {@code Accept}，并带 {@code Accept-Language}
 * 或 {@code Sec-Fetch-*} 之一。缺少这些头而仅伪造 {@code Mozilla/} UA 的，视为爬虫/脚本。</p>
 */
public final class BrowserEvidence {

    private BrowserEvidence() {
    }

    /**
     * 请求是否具备浏览器特征头证据。
     *
     * <p>判定：必须带非空 {@code Accept}，且带 {@code Accept-Language} 或任一 {@code Sec-Fetch-*}。
     * 这样既能放行所有现代浏览器（含媒体 {@code <audio>} 请求），又不强制旧版浏览器提供
     * {@code Sec-Fetch-*}（它们仍会带 {@code Accept-Language}）。</p>
     */
    public static boolean hasFetchEvidence(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String accept = request.getHeader("Accept");
        if (accept == null || accept.isBlank()) {
            return false;
        }
        String acceptLanguage = request.getHeader("Accept-Language");
        if (acceptLanguage != null && !acceptLanguage.isBlank()) {
            return true;
        }
        return request.getHeader("Sec-Fetch-Mode") != null
                || request.getHeader("Sec-Fetch-Site") != null
                || request.getHeader("Sec-Fetch-Dest") != null;
    }
}
