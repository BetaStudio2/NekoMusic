package com.neko.music.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 系统设置清单的自洽性：清单是后台可改配置的唯一出入口，条目本身写错（键重复、出厂值越界、
 * 类型与取值对不上）会让后台页与读取端一起出错，因此在这里做静态校验。
 */
class SystemSettingRegistryTest {

    @Test
    @DisplayName("键唯一、不可为空")
    void keysAreUniqueAndNonBlank() {
        Set<String> seen = new HashSet<>();
        for (SystemSettingDefinition definition : SystemSettingRegistry.all()) {
            String key = definition.key();
            assertNotNull(key);
            assertFalse(key.isBlank(), "配置键不能为空");
            assertTrue(seen.add(key), "配置键重复: " + key);
        }
    }

    @Test
    @DisplayName("mysql.* 不进清单：连库前无从读取")
    void mysqlIsNotManaged() {
        for (SystemSettingDefinition definition : SystemSettingRegistry.all()) {
            assertFalse(definition.key().startsWith("mysql."),
                    "mysql.* 属于启动引导信息，不能由数据库覆盖: " + definition.key());
        }
    }

    @Test
    @DisplayName("展示信息与出厂值齐备")
    void metadataIsPresent() {
        for (SystemSettingDefinition definition : SystemSettingRegistry.all()) {
            assertNotNull(definition.group(), definition.key());
            assertNotNull(definition.type(), definition.key());
            assertNotNull(definition.defaultValue(), definition.key());
            assertFalse(definition.label().isBlank(), definition.key());
            assertFalse(definition.description().isBlank(), definition.key());
        }
    }

    @Test
    @DisplayName("数值项：出厂值可解析且在声明范围内")
    void numericDefaultsAreWithinRange() {
        for (SystemSettingDefinition definition : SystemSettingRegistry.all()) {
            Long min = definition.min();
            Long max = definition.max();
            switch (definition.type()) {
                case INT, LONG -> {
                    // 数值项允许空出厂值，表示「未设置」（读取端会跳过）
                    assertRange(definition, blankOrDefault(definition));
                    assertEquals(min == null, max == null, "范围应成对声明: " + definition.key());
                }
                case DOUBLE -> {
                    String raw = definition.defaultValue().trim();
                    double parsed = raw.isEmpty() ? 0.0 : Double.parseDouble(raw);
                    assertTrue(Double.isFinite(parsed), definition.key());
                    if (min != null) {
                        assertTrue(parsed >= min, definition.key() + " 出厂值低于下限");
                    }
                    if (max != null) {
                        assertTrue(parsed <= max, definition.key() + " 出厂值高于上限");
                    }
                }
                default -> {
                    assertEquals(null, min, "非数值项不应声明下限: " + definition.key());
                    assertEquals(null, max, "非数值项不应声明上限: " + definition.key());
                }
            }
        }
    }

    @Test
    @DisplayName("开关项的出厂值只能是 true / false")
    void flagDefaultsAreBooleans() {
        for (SystemSettingDefinition definition : SystemSettingRegistry.all()) {
            if (definition.type() != SystemSettingDefinition.Type.BOOL) {
                continue;
            }
            String value = definition.defaultValue().trim().toLowerCase(Locale.ROOT);
            assertTrue(value.equals("true") || value.equals("false"),
                    definition.key() + " 的出厂值不是布尔");
        }
    }

    @Test
    @DisplayName("列表项：出厂值按行分隔且无空行")
    void listDefaultsHaveNoBlankLines() {
        for (SystemSettingDefinition definition : SystemSettingRegistry.all()) {
            if (definition.type() != SystemSettingDefinition.Type.LIST) {
                continue;
            }
            String raw = definition.defaultValue();
            if (raw.isEmpty()) {
                continue; // 空出厂值表示「无条目」，合法
            }
            for (String line : raw.split("\\r?\\n", -1)) {
                assertEquals(line.trim(), line, definition.key() + " 的出厂值含多余空白");
                assertFalse(line.isEmpty(), definition.key() + " 的出厂值含空行");
            }
        }
    }

    @Test
    @DisplayName("敏感项：出厂值必须为空，避免把密钥写进代码")
    void secretDefaultsAreBlank() {
        for (SystemSettingDefinition definition : SystemSettingRegistry.all()) {
            if (!definition.secret()) {
                continue;
            }
            assertTrue(definition.defaultValue().isBlank(),
                    "敏感项不应有出厂值: " + definition.key());
        }
    }

    /** 整数出厂值转成数值；空值按 0 处理（表示「未设置」，不参与范围校验起步点） */
    private static long blankOrDefault(SystemSettingDefinition definition) {
        String raw = definition.defaultValue().trim();
        return raw.isEmpty() ? 0L : Long.parseLong(raw);
    }

    private static void assertRange(SystemSettingDefinition definition, long parsed) {
        if (definition.min() != null) {
            assertTrue(parsed >= definition.min(), definition.key() + " 出厂值低于下限");
        }
        if (definition.max() != null) {
            assertTrue(parsed <= definition.max(), definition.key() + " 出厂值高于上限");
        }
    }
}
