package com.neko.music.i18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 文案表：查表、占位、兜底，以及「代码里用到的键都必须有译文」的覆盖闸门。
 *
 * <p>键是命名空间标识（{@code music.upload.unsupported_audio}），缺一条译文时用户会直接看到
 * 键名本身，所以覆盖检查不是锦上添花：新增文案忘记登记时这条用例必须先红。</p>
 */
class ApiMessagesTest {

    private static final Pattern KEY_USE =
            Pattern.compile("ApiMessages\\.translate\\(\\s*\"([a-z][a-z0-9_]*(?:\\.[a-z0-9_]+)+)\"");

    @AfterEach
    void tearDown() {
        RequestLocale.clear();
    }

    @Test
    void translatesByCurrentLocale() {
        assertEquals("Unsupported endpoint.", ApiMessages.translateAs(RequestLocale.EN,
                "common.unsupported_endpoint"));
        assertEquals("不支援的介面", ApiMessages.translateAs("zh-TW", "common.unsupported_endpoint"));
        assertEquals("未対応のエンドポイントです", ApiMessages.translateAs("ja", "common.unsupported_endpoint"));
        assertEquals("지원하지 않는 엔드포인트입니다", ApiMessages.translateAs("ko", "common.unsupported_endpoint"));
        assertEquals("不支持的接口", ApiMessages.translateAs(RequestLocale.DEFAULT,
                "common.unsupported_endpoint"));
    }

    @Test
    void fillsPlaceholdersInBothLocales() {
        assertEquals("Removed 3 track(s) from the playlist.",
                ApiMessages.translateAs(RequestLocale.EN, "playlist.remove.done", 3));
        assertEquals("已从歌单中移除 3 首音乐",
                ApiMessages.translateAs(RequestLocale.DEFAULT, "playlist.remove.done", 3));
    }

    @Test
    void unknownKeyFallsBackToKeyItself() {
        assertEquals("nope.not.a.key", ApiMessages.translate("nope.not.a.key"));
        assertEquals("nope.not.a.key", ApiMessages.translate("nope.not.a.key", "arg"));
    }

    @Test
    void nullAndEmptyAreSafe() {
        assertEquals(null, ApiMessages.translate((String) null));
        assertEquals("", ApiMessages.translate(""));
    }

    @Test
    void everyRegisteredLanguageCoversEveryKey() {
        // 所有语言的键集合必须与源语言完全一致：少一条就是「这门语言漏翻了」，多一条就是残留的
        // 旧键。语言是注册表驱动的，加语言时这条用例自动把它纳入检查。
        Set<String> source = keysOf("messages_zh_CN.properties");
        assertFalse(source.isEmpty(), "中文文案表为空");
        assertEquals(RequestLocale.supported(), ApiMessages.loadedLocales(), "注册的语言必须都装载了文案表");
        for (String tag : RequestLocale.supported()) {
            assertTrue(ApiMessages.tableSize(tag) > 0, tag + " 没有装载到文案表");
            assertEquals(source.size(), ApiMessages.tableSize(tag), tag + " 的条目数与源语言不一致");
            Set<String> keys = keysOf("messages_" + tag.replace('-', '_') + ".properties");
            Set<String> missing = new LinkedHashSet<>(source);
            missing.removeAll(keys);
            assertEquals(Set.of(), missing, tag + " 缺少译文");
            Set<String> extra = new LinkedHashSet<>(keys);
            extra.removeAll(source);
            assertEquals(Set.of(), extra, tag + " 存在源语言没有的键");
        }
    }

    @Test
    void fallsBackAlongTheDeclaredChain() {
        // zh-TW 缺译文时回落 zh-CN；ja 缺译文时回落 en（注册表 languages.properties 声明）
        assertEquals("zh-CN", Languages.fallbackOf("zh-TW"));
        assertEquals("en", Languages.fallbackOf("ja"));
        assertEquals("zh-CN", Languages.fallbackOf("en"));
        // 未注册的语言也要能一路回落到源语言，而不是返回 null 或抛异常
        assertEquals(RequestLocale.DEFAULT, Languages.fallbackOf("xx-YY"));
        assertEquals("不支持的接口", ApiMessages.translateAs("xx-YY", "common.unsupported_endpoint"));
    }

    @Test
    void sourceIgnoresRequestLanguage() {
        // 公开共享缓存接口用 source()：无论请求什么语言都返回源语言文案
        assertEquals("获取最新音乐成功", ApiMessages.source("music.latest_ok"));
        assertEquals("不支持的接口", ApiMessages.source("common.unsupported_endpoint"));
        assertEquals("未知的键", ApiMessages.source("未知的键"));
    }

    @Test
    void everyKeyUsedInCodeIsRegistered() {
        Path root = Path.of("src", "main", "java");
        assumeTrue(Files.isDirectory(root), "需要在 backend 模块目录下运行");
        Set<String> registered = ApiMessages.keys();
        Set<String> missing = new HashSet<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path p : paths.filter(f -> f.toString().endsWith(".java")).toList()) {
                Matcher m = KEY_USE.matcher(read(p));
                while (m.find()) {
                    if (!registered.contains(m.group(1))) {
                        missing.add(m.group(1) + "  (" + p + ")");
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertEquals(Set.of(), missing, "以下键在代码里用了但没有登记文案");
    }

    private static Set<String> keysOf(String resource) {
        String text;
        try (var in = ApiMessagesTest.class.getResourceAsStream("/i18n/" + resource)) {
            assertTrue(in != null, "缺少资源 /i18n/" + resource);
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Set<String> keys = new LinkedHashSet<>();
        for (String line : text.split("\n")) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            int i = line.indexOf('=');
            assertNotEquals(-1, i, "文案行缺少 = ：" + line);
            keys.add(line.substring(0, i).trim());
        }
        return keys;
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
