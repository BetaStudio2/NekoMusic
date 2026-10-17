package com.neko.music.i18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
 *
 * <p>「支持哪些语言」由 {@link Languages} 的注册表决定，这里只负责把请求头映到某个已注册语言。</p>
 */
public final class RequestLocale {

    /** 默认语言：没有可用的 {@code Accept-Language} 时用它，也是本站文案的源语言。 */
    public static final String DEFAULT = Languages.SOURCE;

    /** 英文标签的便捷常量（日志与测试用）。 */
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

    /** 已注册的语言标签（含源语言）。 */
    public static List<String> supported() {
        return Languages.tags();
    }

    /**
     * 解析 {@code Accept-Language}，返回本站支持的语言标签。
     *
     * <p>按 q 值从大到小取，同 q 时按请求头里的出现顺序；逐个候选去 {@link Languages} 里匹配，
     * 第一个匹配上的就是结果。全都匹配不上（含只有 {@code fr-FR,de} 这类本站没做的语言、以及
     * {@code *}）时回落 {@link #DEFAULT}；{@code q=0} 表示「明确不接受」，不能当候选。</p>
     *
     * <p>不使用 {@code HttpServletRequest#getLocale()}：容器对通配、q 值与兜底的处理各家不同，
     * 且本站要求该头必须存在，自己解析口径才稳定。</p>
     */
    public static String resolve(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return DEFAULT;
        }
        // 浏览器 / 客户端发出的 Accept-Language 取值高度集中（同一种语言组合反复出现），
        // 而解析要切分、排序再逐个匹配注册表。按请求头原文缓存结果，命中就是一次 map 查找。
        String cached = RESOLVED.get(acceptLanguage);
        if (cached != null) {
            return cached;
        }
        String resolved = resolveUncached(acceptLanguage);
        if (RESOLVED.size() >= RESOLVED_MAX) {
            RESOLVED.clear();
        }
        RESOLVED.put(acceptLanguage, resolved);
        return resolved;
    }

    /** 解析结果缓存上限：请求头由客户端决定，必须有界（超限整体清空）。 */
    private static final int RESOLVED_MAX = 256;

    private static final Map<String, String> RESOLVED = new java.util.concurrent.ConcurrentHashMap<>();

    private static String resolveUncached(String acceptLanguage) {
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
            if (quality > 0f) {
                candidates.add(new Candidate(quality, i, tag));
            }
        }
        candidates.sort((a, b) -> a.quality() != b.quality()
                ? Float.compare(b.quality(), a.quality())
                : Integer.compare(a.order(), b.order()));
        for (Candidate candidate : candidates) {
            if (candidate.tag().equals("*")) {
                return DEFAULT;
            }
            String matched = Languages.match(candidate.tag());
            if (matched != null) {
                return matched;
            }
        }
        return DEFAULT;
    }
}
