package com.neko.music.seo;

import java.util.regex.Pattern;

/**
 * 判断请求是否应收到服务端渲染的 SEO HTML。
 *
 * <p>详情页、歌单页与静态页（首页 / 下载 / 关于等）共用同一套判定，避免多处逻辑漂移。
 * 普通浏览器识别到 {@code Mozilla/} 前缀即返回 SPA；搜索引擎、AI 抓取器、链接预览
 * 机器人、监控与命令行客户端一律返回可被无 JS 解析的 HTML。</p>
 *
 * <p>UA 列表按用途分组维护，新增抓取器时优先补到最贴近的分组里，不要堆进单个巨型正则。
 * 注意：不要匹配「带有 Mozilla 前缀的应用内浏览器」（如 MicroMessenger、QQ浏览器、
 * SogouMobileBrowser、Electron 桌面端），它们能执行 JS，必须继续拿到 SPA。</p>
 */
public final class UserAgentClassifier {

    /** 通用抓取器关键词，覆盖绝大多数机器人 UA。 */
    private static final Pattern GENERIC_FETCHERS = Pattern.compile(
            "(?i)(?:bot|crawler|crawl|spider|slurp|fetcher|archiver|harvester|scraper|scraperbot|"
                    + "spiderling|inspectiontool|validator|preview|feedfetcher|feedly|qwantify|yeti)");

    /** 搜索引擎官方爬虫 / 索引机器人。 */
    private static final Pattern SEARCH_ENGINE_BOTS = Pattern.compile(
            "(?i)(?:googlebot|google-inspectiontool|google-extended|googleother|google-cloudvertexbot|"
                    + "google-safety|google-read-aloud|storebot-google|adsbot-google|mediapartners-google|"
                    + "apis-google|bingbot|bingpreview|adidxbot|msnbot|"
                    + "baiduspider|sogouspider|sogou web spider|sogou news spider|360spider|qihoobot|"
                    + "yisouspider|shenmaspider|youdaobot|sosospider|petalbot|toutiaospider|bytespider|"
                    + "yandexbot|yandeximages|yandexvideo|yandexmedia|yandexmobilebot|yandexmetrika|"
                    + "naverbot|yeti|seznambot|exabot|gigabot|mojeekbot|duckduckbot|duckassistbot|"
                    + "applebot|applebot-extended|slurp)");

    /** 社交平台 / 聊天软件的链接预览抓取器。 */
    private static final Pattern LINK_PREVIEW_BOTS = Pattern.compile(
            "(?i)(?:facebookexternalhit|facebookcatalog|facebot|facebookbot|"
                    + "meta-externalagent|meta-externalfetcher|"
                    + "linkedinbot|twitterbot|discordbot|telegrambot|whatsapp|pinterest|"
                    + "slackbot|slack-imgproxy|skypeuripreview|vkshare|viber|"
                    + "redditbot|embedly|iframely|outbrain|snapchat|nuzzel|bitlybot|tumblr|"
                    + "qqurlrichservice|qqshareproxy)");

    /** 生成式引擎 / AI 训练与检索抓取器（GEO）。 */
    private static final Pattern AI_CRAWLERS = Pattern.compile(
            "(?i)(?:gptbot|oai-searchbot|chatgpt|openai|perplexity|claudebot|claude-web|claude-user|"
                    + "anthropic|cohere|ccbot|bytespider|youbot|ai2bot|imagesift|diffbot|omgili|"
                    + "webzio|factoid|timpibot|bravebot|kagibot|amazonbot|duckassistbot|"
                    + "google-extended|google-cloudvertexbot|applebot-extended)");

    /** SEO / 外链分析工具爬虫。 */
    private static final Pattern SEO_ANALYTICS_BOTS = Pattern.compile(
            "(?i)(?:semrush|ahrefs|mj12bot|dotbot|rogerbot|megaindex|serpstat|dataforseo|"
                    + "screaming frog|screamingfrog|blexbot|barkrowler|linkdex|seokicks|"
                    + "piplbot|zoominfobot|spbot|peer39|majestic|sistrix|rytebot|cognitiveseo)");

    /** 命令行 / 库 / 原生 App 抓取客户端（无 JS 渲染能力）。 */
    private static final Pattern HTTP_CLIENTS = Pattern.compile(
            "(?i)(?:curl|wget|httpclient|okhttp|python|java/|dalvik|"
                    + "aiohttp|httpx|libwww-perl|go-http-client|node-fetch|undici|axios|superagent|"
                    + "scrapy|mechanize|httrack|postman|insomnia|restsharp|guzzle|"
                    + "winhttp|powershell|cfnetwork|dart|libmpv|mpv/|vlc|lavf|ffmpeg|ffprobe)");

    /** 无头浏览器 / 自动化测试框架。 */
    private static final Pattern HEADLESS_BROWSERS = Pattern.compile(
            "(?i)(?:headlesschrome|headless|phantomjs|selenium|playwright|puppeteer|"
                    + "webdriver|cypress|chrome-lighthouse|lighthouse|slimerjs)");

    /** 站点监控 / 归档 / 抓取存档服务。 */
    private static final Pattern MONITORS_ARCHIVERS = Pattern.compile(
            "(?i)(?:uptimerobot|pingdom|statuscake|site24x7|gtmetrix|newrelicpinger|datadog|"
                    + "betteruptime|hetrixtools|freshping|montastic|internetseer|dotcom-monitor|"
                    + "nodeping|uptime-kuma|nagios|zabbix|librenms|"
                    + "ia_archiver|archive\\.org_bot|commoncrawl|heritrix|wayback|cc-main)");

    private UserAgentClassifier() {
    }

    public static boolean shouldRenderSeo(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return true;
        }
        String normalized = userAgent.trim();
        if (matchesAny(normalized,
                GENERIC_FETCHERS,
                SEARCH_ENGINE_BOTS,
                LINK_PREVIEW_BOTS,
                AI_CRAWLERS,
                SEO_ANALYTICS_BOTS,
                HTTP_CLIENTS,
                HEADLESS_BROWSERS,
                MONITORS_ARCHIVERS)) {
            return true;
        }
        // Real browsers conventionally identify themselves with Mozilla. A
        // non-Mozilla client is treated as a fetcher and receives SEO HTML.
        return !normalized.contains("Mozilla/");
    }

    private static boolean matchesAny(String userAgent, Pattern... patterns) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(userAgent).find()) {
                return true;
            }
        }
        return false;
    }
}
