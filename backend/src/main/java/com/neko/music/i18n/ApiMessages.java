package com.neko.music.i18n;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * API 响应文案的多语言表。
 *
 * <p>文案在调用点用「命名空间的键」写出（如 {@code music.upload.unsupported_audio}），键到各语言
 * 文案的映射放在 {@code resources/i18n/messages_<tag>.properties}；这是标准的 i18n 结构：
 * 代码里只有稳定的键，文案改动不进代码评审、新增语言只加一个文件。</p>
 *
 * <p><b>查表顺序</b>：当前语言 → {@link RequestLocale#DEFAULT}（源语言兜底）→ 键本身。因此缺一条
 * 译文只是「回落中文 / 暴露键名」，不会抛异常、不会把接口打挂；键与文案的对应关系有单测锁定
 * （{@code ApiMessagesTest}），防止漏登记或写错键。</p>
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

    /** 语言标签 → 译文表资源路径。 */
    private static final Map<String, String> TABLES = Map.of(
            RequestLocale.DEFAULT, "/i18n/messages_zh_CN.properties",
            RequestLocale.EN, "/i18n/messages_en.properties");

    private static final Map<String, Map<String, String>> LOADED = loadTables();

    private ApiMessages() {
    }

    /** 按当前请求语言翻译文案；查不到译文时回落源语言，仍查不到则返回键本身。 */
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
        String text = lookup(RequestLocale.current(), key);
        if (text == null) {
            text = lookup(RequestLocale.DEFAULT, key);
        }
        if (text == null) {
            text = key;
        }
        return args == null || args.length == 0 ? text : fill(text, args);
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

    private static String fill(String template, Object[] args) {
        String result = template;
        for (int i = 0; i < args.length; i++) {
            result = result.replace("{" + i + "}", args[i] == null ? "" : String.valueOf(args[i]));
        }
        return result;
    }

    private static Map<String, Map<String, String>> loadTables() {
        Map<String, Map<String, String>> tables = new HashMap<>();
        TABLES.forEach((locale, path) -> {
            Map<String, String> table = loadTable(path);
            if (!table.isEmpty()) {
                tables.put(locale, table);
            }
        });
        return tables;
    }

    /** 读取一张 {@code 键=文案} 表；资源缺失或读取异常时返回空表（外层降级为源语言 / 键名）。 */
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
        return TABLES.keySet().stream().map(tag -> tag.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }
}
