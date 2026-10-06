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

    /**
     * 安全扫描 / 漏洞探测工具（sqlmap、Nikto、Nmap、目录爆破等）。
     * 这类工具常自定义或伪装 UA，内置关键词表若不覆盖就会被当作普通客户端放行。
     */
    private static final Pattern SECURITY_SCANNERS = Pattern.compile(
            "(?i)(?:sqlmap|nikto|nmap|masscan|zgrab|nuclei|acunetix|nessus|openvas|"
                    + "wpscan|gobuster|ffuf|feroxbuster|dirbuster|"
                    + "xray|burpsuite|burp|zaproxy|owasp[ _-]?zap|whatweb|w3af|arachni|skipfish|"
                    + "jaeles|commix|dalfox|wapiti|netsparker|qualys|appscan|"
                    + "hydra|medusa|sslyze|sslscan|testssl|joomscan|droopescan)");


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
                MONITORS_ARCHIVERS,
                SECURITY_SCANNERS)) {
            return true;
        }
        // Real browsers conventionally identify themselves with Mozilla. A
        // non-Mozilla client is treated as a fetcher and receives SEO HTML.
        return !normalized.contains("Mozilla/");
    }

    /**
     * 结合浏览器特征头判断是否应渲染 SEO HTML。
     *
     * <p>UA 判定为爬虫（{@link #shouldRenderSeo(String)}）时返回 {@code true}；
     * UA 虽含 {@code Mozilla/} 但缺少浏览器特征头（疑似伪造的未知爬虫）时同样返回 {@code true}；
     * 只有「UA 结构像真浏览器 + 特征头齐全」才返回 {@code false}（走前端 SPA）。</p>
     */
    public static boolean shouldRenderSeo(String userAgent, boolean hasBrowserFetchEvidence) {
        if (shouldRenderSeo(userAgent)) {
            return true;
        }
        return !hasBrowserFetchEvidence;
    }

    /**
     * 判断该 UA 是否属于「不应访问 JSON API」的爬虫 / 无头浏览器 / 命令行工具。
     *
     * <p>用于防爬过滤器：浏览器以真实 UA 通过 fetch 访问 /api，永远命中 false；原生客户端
     * （Android / PC / 播放器）虽然不含 Mozilla，但走 {@link #isNativeClient} 一并放行，
     * 避免拦截掉正常 App 与音视频直链请求。空 UA 也放行（交 IP 限流兜底），尽量保守、不误伤。</p>
     */
    public static boolean isBotForApi(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return false;
        }
        String normalized = userAgent.trim();
        if (isNativeClient(normalized)) {
            return false;
        }
        return matchesAny(normalized,
                GENERIC_FETCHERS,
                SEARCH_ENGINE_BOTS,
                LINK_PREVIEW_BOTS,
                AI_CRAWLERS,
                SEO_ANALYTICS_BOTS,
                HTTP_CLIENTS,
                HEADLESS_BROWSERS,
                MONITORS_ARCHIVERS,
                SECURITY_SCANNERS);
    }

    /** 原生客户端 / 播放器 / 桌面端 UA 一律放行（它们确实需要访问 API 或媒体直链，且不含 Mozilla）。 */
    public static boolean isNativeClient(String ua) {
        if (ua == null || ua.isBlank()) {
            return false;
        }
        String lower = ua.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("okhttp") || lower.contains("dalvik")
                || lower.contains("libmpv") || lower.contains("mpv/")
                || lower.startsWith("vlc") || lower.contains("ffmpeg")
                || lower.contains("ffprobe") || lower.contains("android")
                || lower.contains("electron") || lower.startsWith("qt")
                || lower.contains("qts") || lower.contains("qtwebengine")
                // 本站 PC 桌面端：ApiClient 用 QNetworkRequest 默认不发送 UA，封面请求为 "NekoMusic Qt"
                || lower.contains("nekomusic");
    }

    /**
     * 判断 UA 是否「结构上像一个真实浏览器」。
     *
     * <p>未知/小众爬虫常自定义 UA 或只伪造 {@code Mozilla/} 前缀，缺少浏览器内核标记；
     * 真浏览器与应用内 WebView 则一定带内核标记。注意 iOS WKWebView（微信 / QQ / 支付宝等）
     * 的 UA <b>常省略 {@code Safari/}、{@code Version/}</b>，因此这里只要求存在内核标记，
     * 不强制 Safari 版本串，避免误伤应用内浏览器。</p>
     */
    public static boolean looksLikeRealBrowser(String userAgent) {
        if (userAgent == null || userAgent.isBlank() || !userAgent.contains("Mozilla/")) {
            return false;
        }
        // AppleWebKit/（Chrome/Chromium/Safari/Edge/Opera/iOS WebView）
        // Gecko/（Firefox）、Trident/（旧 Edge/IE11）
        return userAgent.contains("AppleWebKit/")
                || userAgent.contains("Gecko/")
                || userAgent.contains("Trident/");
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
