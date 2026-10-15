package com.neko.music.i18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 单次请求的响应语言，随线程传递（过滤器写入、响应写出时读取）。
 *
 * <p>为什么用 ThreadLocal：文案在 {@code ApiServlet}、{@code HandlerResponses} 与几个过滤器里
 * 写出，这些方法只拿得到 {@code HttpServletResponse}。若为了拿语言而给每个响应方法多加一个
 * {@code HttpServletRequest} 参数，等于把两百多个调用点全部改一遍；语言在请求进入时解析一次、
 * 整条链路共用，是更小的改动面。</p>
 *
 * <p>必须在过滤器里 {@code finally} 调 {@link #clear()}：Jetty 的线程会复用，残留的语言会串到
 * 下一个请求上。</p>
 */
public final class RequestLocale {

    /** 默认语言：没有可用的 {@code Accept-Language} 时用它，也是本站文案的源语言。 */
    public static final String DEFAULT = "zh-CN";

    /** 支持的语言（与 {@code i18n/messages_<tag>.properties} 一一对应）。 */
    public static final String EN = "en";

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private RequestLocale() {
    }

    public static void set(String locale) {
        CURRENT.set(locale == null || locale.isBlank() ? DEFAULT : locale);
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** 当前请求的语言；不在请求线程里（启动预热、定时任务、单测）时返回 {@link #DEFAULT}。 */
    public static String current() {
        String locale = CURRENT.get();
        return locale == null ? DEFAULT : locale;
    }

    /**
     * 解析 {@code Accept-Language}，返回本站支持的语言标签。
     *
     * <p>按 q 值从大到小取，只认「站点支持的语言」这一层（{@code zh} / {@code en} 前缀），
     * 取不到就回落 {@link #DEFAULT}。不使用 {@code HttpServletRequest#getLocale()}：容器对
     * 通配、q 值与兜底的处理各家不同，且本站要求该头必须存在，自己解析口径才稳定。</p>
     */
    public static String resolve(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return DEFAULT;
        }
        record Candidate(float quality, int order, String tag) {
        }
        List<Candidate> candidates = new ArrayList<>();
        String[] parts = acceptLanguage.split(",");
        for (int i = 0; i < parts.length; i++) {
            String[] pair = parts[i].split(";");
            String tag = pair[0].trim();
            if (tag.isEmpty()) {
                continue;
            }
            float quality = 1.0f;
            for (int j = 1; j < pair.length; j++) {
                String param = pair[j].trim();
                if (param.regionMatches(true, 0, "q=", 0, 2)) {
                    try {
                        quality = Float.parseFloat(param.substring(2).trim());
                    } catch (NumberFormatException ignored) {
                        quality = 0f;
                    }
                }
            }
            // q=0 表示「明确不接受」，不能拿它当候选
            if (quality > 0f) {
                candidates.add(new Candidate(quality, i, tag));
            }
        }
        candidates.sort((a, b) -> a.quality() != b.quality()
                ? Float.compare(b.quality(), a.quality())
                : Integer.compare(a.order(), b.order()));
        for (Candidate candidate : candidates) {
            String language = candidate.tag().toLowerCase(Locale.ROOT);
            if (language.equals("*")) {
                return DEFAULT;
            }
            if (language.startsWith("zh")) {
                return DEFAULT;
            }
            if (language.startsWith("en")) {
                return EN;
            }
        }
        return DEFAULT;
    }
}
