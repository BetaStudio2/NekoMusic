package com.neko.music.i18n;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 站点语言注册表，读 {@code resources/i18n/languages.properties}，是「本站有哪些语言」的唯一清单。
 *
 * <p>一行一门语言，用 {@code |} 分隔：{@code 标签|自称|匹配前缀|缺译文回落}。加一门语言＝放一个
 * {@code messages_<标签>.properties} 再加一行注册，代码不用动；标签里的 {@code -} 在文件名里写成
 * {@code _}（{@code zh-TW} → {@code messages_zh_TW.properties}）。</p>
 *
 * <p>刻意外置成资源文件而不是写在代码里的常量数组：语言清单属于「数据」，加语言不该进代码评审，
 * 也不该让 {@link RequestLocale} / {@link ApiMessages} 各维护一份。</p>
 *
 * <p>匹配规则：{@code Accept-Language} 的子标签按注册前缀做「最长前缀优先」匹配，因此
 * {@code zh-Hant-TW} → {@code zh-TW}、{@code zh-Hans-CN} → {@code zh-CN}、{@code en-GB} → {@code en}。
 * 不依赖 {@code ResourceBundle}/{@code Locale} 的内置查找——JDK 对繁体/地区的候选顺序在不同版本上
 * 并不一致，本站自己按注册表匹配，口径固定可测。</p>
 *
 * <p>注册表缺失或不可读时只保留源语言：语言是锦上添花，不该让服务起不来。</p>
 */
final class Languages {

    /** 源语言：注册表读不到时的唯一兜底，也是所有语言的最终回落目标。 */
    static final String SOURCE = "zh-CN";

    private static final String REGISTRY_PATH = "/i18n/languages.properties";

    /** 一门已注册的语言。 */
    record Entry(String tag, String nativeName, List<String> prefixes, String fallback) {
    }

    private static final Map<String, Entry> BY_TAG;
    /** 「前缀 → 语言标签」，已按前缀长度降序，保证最长前缀优先。 */
    private static final List<Map.Entry<String, String>> PREFIXES;

    static {
        Map<String, Entry> byTag = new LinkedHashMap<>();
        List<Map.Entry<String, String>> prefixes = new ArrayList<>();
        for (Entry entry : parse(readRegistry())) {
            byTag.put(entry.tag(), entry);
            for (String prefix : entry.prefixes()) {
                prefixes.add(Map.entry(prefix, entry.tag()));
            }
        }
        if (byTag.isEmpty()) {
            byTag.put(SOURCE, new Entry(SOURCE, "简体中文", List.of("zh"), ""));
            prefixes.add(Map.entry("zh", SOURCE));
        }
        prefixes.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));
        // 不能用 Map.copyOf：它不保证迭代顺序，而「源语言在最前」是注册表用例锁住的不变量
        BY_TAG = Collections.unmodifiableMap(new LinkedHashMap<>(byTag));
        PREFIXES = List.copyOf(prefixes);
    }

    private Languages() {
    }

    /** 已注册的语言标签，按注册表顺序（源语言在最前）。 */
    static List<String> tags() {
        return List.copyOf(BY_TAG.keySet());
    }

    /** 该语言的自称（日志/后台展示用）。 */
    static String nativeName(String tag) {
        Entry entry = BY_TAG.get(tag);
        return entry == null ? tag : entry.nativeName();
    }

    /**
     * 该语言缺译文时的下一站。
     *
     * <p>没声明回落、或声明成自己时返回 {@link #SOURCE}；因此「顺着回落链走下去」必然终止于源语言。</p>
     */
    static String fallbackOf(String tag) {
        Entry entry = BY_TAG.get(tag);
        if (entry == null || entry.fallback().isEmpty() || entry.fallback().equals(tag)) {
            return SOURCE;
        }
        return entry.fallback();
    }

    /** 把一个 {@code Accept-Language} 子标签映到站内语言；没有匹配返回 {@code null}。 */
    static String match(String tag) {
        String lower = tag.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> prefix : PREFIXES) {
            String key = prefix.getKey();
            if (lower.equals(key) || lower.startsWith(key + "-")) {
                return prefix.getValue();
            }
        }
        return null;
    }

    /** 语言标签对应的文案表资源路径。 */
    static String tablePath(String tag) {
        return "/i18n/messages_" + tag.replace('-', '_') + ".properties";
    }

    private static List<Entry> parse(String content) {
        List<Entry> entries = new ArrayList<>();
        for (String raw : content.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.charAt(0) == '#') {
                continue;
            }
            String[] parts = line.split("\\|", -1);
            if (parts.length < 3) {
                continue;
            }
            String tag = parts[0].trim();
            if (tag.isEmpty()) {
                continue;
            }
            List<String> prefixes = new ArrayList<>();
            for (String prefix : parts[2].split(",")) {
                String value = prefix.trim().toLowerCase(Locale.ROOT);
                if (!value.isEmpty()) {
                    prefixes.add(value);
                }
            }
            if (prefixes.isEmpty()) {
                continue;
            }
            String fallback = parts.length > 3 ? parts[3].trim() : "";
            entries.add(new Entry(tag, parts[1].trim(), List.copyOf(prefixes), fallback));
        }
        return entries;
    }

    private static String readRegistry() {
        InputStream in = Languages.class.getResourceAsStream(REGISTRY_PATH);
        if (in == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append('\n');
            }
            return content.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
