package com.neko.music.seo;

import java.util.regex.Pattern;

/**
 * 判断请求是否应收到服务端渲染的 SEO HTML。
 *
 * <p>详情页与静态页（首页 / 下载 / 关于等）共用同一套判定，避免两处逻辑漂移。
 * 普通浏览器识别到 Mozilla 前缀即返回 SPA；其余（curl、搜索引擎、AI 抓取器、
 * 链接预览机器人）一律返回可被无 JS 解析的 HTML。
 */
public final class UserAgentClassifier {

    private static final Pattern CRAWLER_PATTERN = Pattern.compile(
            "(?i)(?:bot|crawler|spider|slurp|bingpreview|googlebot|google-extended|googleother|"
                    + "google-inspectiontool|bingbot|adidxbot|duckduckbot|facebookexternalhit|facebot|"
                    + "linkedinbot|twitterbot|discordbot|telegrambot|whatsapp|pinterest|qqshareproxy|"
                    + "bytespider|yandex|baiduspider|sogou|360spider|yisouspider|sosospider|youdaobot|"
                    + "petalbot|semrush|ahrefs|mj12bot|applebot|dotbot|rogerbot|megaindex|serpstatbot|"
                    + "dataforseo|commoncrawl|ia_archiver|archive\\.org_bot|uptimerobot|pingdom|statuscake|"
                    + "gptbot|oai-searchbot|chatgpt-user|perplexitybot|claudebot|claude-web|anthropic-ai|"
                    + "google-cloudvertexbot|ccbot|cohere-ai|youbot|amazonbot|bravebot|kagibot|timpibot|"
                    + "meta-externalagent|meta-externalfetcher|"
                    + "curl|wget|httpclient|okhttp|python-requests|python-urllib|aiohttp|libwww-perl|"
                    + "go-http-client|scrapy|mechanize|headlesschrome|phantomjs|selenium|playwright|puppeteer)"
    );

    private UserAgentClassifier() {
    }

    public static boolean shouldRenderSeo(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return true;
        }
        String normalized = userAgent.trim();
        if (CRAWLER_PATTERN.matcher(normalized).find()) {
            return true;
        }
        // Real browsers conventionally identify themselves with Mozilla. A
        // non-Mozilla client is treated as a fetcher and receives SEO HTML.
        return !normalized.contains("Mozilla/");
    }
}
