package com.neko.music.i18n;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * API 响应文案的多语言表。
 *
 * <p>文案在调用点用「命名空间的键」写出（如 {@code music.upload.unsupported_audio}），键到各语言
 * 文案的映射放在 {@code resources/i18n/messages_<标签>.properties}；这是标准的 i18n 结构：
 * 代码里只有稳定的键，文案改动不进代码评审、加一门语言＝放一个文件并登记到
 * {@code i18n/languages.properties}。</p>
 *
 * <p><b>查表顺序</b>：当前语言 → 该语言声明的回落语言（如 {@code zh-TW → zh-CN}、{@code ja → en}）
 * → {@link RequestLocale#DEFAULT} → 键本身。因此缺一条译文只是「往后回落 / 暴露键名」，不会抛异常、
 * 不会把接口打挂；键与文案的对应关系有单测锁定（{@code ApiMessagesTest}），防止漏登记或写错键。</p>
 *
 * <p>带参数的文案用 {@code {0}}、{@code {1}} 占位（例如
 * {@code playlist.remove.done=已从歌单中移除 {0} 首音乐}），不使用 {@code MessageFormat}——
 * 它对单引号有特殊语义，英文译文里的 {@code don't} 会被吃掉。</p>
 *
 * <p>表文件是给人维护的纯文本：逐行「键=文案」，UTF-8，{@code #} 开头是注释。刻意<b>不用</b>
 * {@code java.util.Properties}——它会以空白 / 冒号截断键，逼得中文写成一堆 {@code \u4e2d\u6587}
 * 转义；这里按「首个 {@code =} 之前为键」解析，键和文案都能原样可读。</p>
 */
public final class ApiMessages {

    private static final Map<String, Map<String, String>> LOADED = loadTables();

    private ApiMessages() {
    }

    /** 按当前请求语言翻译文案；查不到译文时按回落链退到源语言，仍查不到则返回键本身。 */
    public static String translate(String key) {
        return translate(key, new Object[0]);
    }

    /**
     * 按当前请求语言翻译带参文案并填充占位符。
     *
     * <p>无对应译文时，占位符仍会按参数填充——否则用户会看到裸露的 {@code {0}}。</p>
     */
    public static String translate(String key, Object... args) {
        if (key == null || key.isEmpty()) {
            return key;
        }
        return render(lookupChain(RequestLocale.current(), key), key, args);
    }

    /**
     * 取源语言（简体中文）文案，忽略请求语言。
     *
     * <p>给「响应会被共享缓存、必须保持唯一变体」的公开接口用：它们的 {@code Cache-Control} 是
     * {@code public}，一份缓存要发给所有语言的使用者，若 {@code message} 随 {@code Accept-Language}
     * 变化就必须声明 {@code Vary}，等于按语言碎片化缓存。</p>
     */
    public static String source(String key, Object... args) {
        if (key == null || key.isEmpty()) {
            return key;
        }
        return render(lookup(RequestLocale.DEFAULT, key), key, args);
    }

    private static String render(String text, String key, Object[] args) {
        String result = text == null ? key : text;
        return args == null || args.length == 0 ? result : fill(result, args);
    }

    /** 当前语言 → 顺着回落链 → 源语言；都没有译文时返回 {@code null}。 */
    private static String lookupChain(String locale, String key) {
        String current = locale == null || locale.isBlank() ? RequestLocale.DEFAULT : locale;
        while (true) {
            String text = lookup(current, key);
            if (text != null) {
                return text;
            }
            if (RequestLocale.DEFAULT.equals(current)) {
                return null;
            }
            current = Languages.fallbackOf(current);
        }
    }

    private static String lookup(String locale, String key) {
        Map<String, String> table = LOADED.get(locale);
        if (table == null) {
            return null;
        }
        String value = table.get(key);
        return value == null || value.isEmpty() ? null : value;
    }

    /** 某语言已登记的文案条数（供自检与测试）。 */
    public static int tableSize(String locale) {
        Map<String, String> table = LOADED.get(locale);
        return table == null ? 0 : table.size();
    }

    /** 已登记的键集合（源语言表为准，供测试做覆盖检查）。 */
    public static Set<String> keys() {
        Map<String, String> table = LOADED.get(RequestLocale.DEFAULT);
        return table == null ? Set.of() : Set.copyOf(table.keySet());
    }

    /** 已装载文案表的语言标签。 */
    public static List<String> loadedLocales() {
        return List.copyOf(LOADED.keySet());
    }

    private static String fill(String template, Object[] args) {
        String result = template;
        for (int i = 0; i < args.length; i++) {
            result = result.replace("{" + i + "}", args[i] == null ? "" : String.valueOf(args[i]));
        }
        return result;
    }

    private static Map<String, Map<String, String>> loadTables() {
        // LinkedHashMap：按注册表顺序装载，保证 loadedLocales() 与 RequestLocale.supported() 同序
        Map<String, Map<String, String>> tables = new LinkedHashMap<>();
        for (String tag : Languages.tags()) {
            Map<String, String> table = loadTable(Languages.tablePath(tag));
            if (!table.isEmpty()) {
                tables.put(tag, table);
            }
        }
        return Collections.unmodifiableMap(tables);
    }

    /** 读取一张 {@code 键=文案} 表；资源缺失或读取异常时返回空表（外层按回落链降级）。 */
    private static Map<String, String> loadTable(String path) {
        Map<String, String> table = new HashMap<>();
        InputStream in = ApiMessages.class.getResourceAsStream(path);
        if (in == null) {
            return table;
        }
        // 必须显式 UTF-8：默认字符集在不同机器上未必是 UTF-8，中文文案会变乱码
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') {
                    continue;
                }
                int split = line.indexOf('=');
                if (split <= 0) {
                    continue;
                }
                String key = line.substring(0, split).trim();
                String value = line.substring(split + 1).trim();
                if (!key.isEmpty() && !value.isEmpty()) {
                    table.put(key, value);
                }
            }
        } catch (Exception e) {
            return new HashMap<>();
        }
        return table;
    }

    /** 供测试直接指定语言调用，不依赖请求线程。 */
    static String translateAs(String locale, String key, Object... args) {
        String previous = RequestLocale.current();
        try {
            RequestLocale.set(locale);
            return translate(key, args);
        } finally {
            RequestLocale.set(previous);
        }
    }

    /** 支持的语言标签（小写）。 */
    public static Set<String> supportedLocales() {
        return LOADED.keySet().stream().map(tag -> tag.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
    }
}
