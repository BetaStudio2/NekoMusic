package com.neko.music.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据库系统设置叠加到配置树上的行为：登记键生效、未登记键被忽略、非法值只跳过该条。
 *
 * <p>这些用例不读磁盘上的 config.yml：{@link ConfigManager#applyOverrides} 在配置树为空时
 * 会现场建树，因此可以直接断言「数据库值能否走到读取端」。</p>
 */
class ConfigManagerOverrideTest {

    @Test
    @DisplayName("登记键：按点分路径合并后读取端立即生效")
    void registeredKeysReachGetters() {
        ConfigManager config = new ConfigManager();
        Map<String, String> overrides = new LinkedHashMap<>();
        overrides.put("port", "12345");
        overrides.put("video_render.worker_threads", "7");
        overrides.put("network.crawler_protection_enabled", "false");
        overrides.put("smtp.host", "smtp.example.com");

        config.applyOverrides(overrides);

        assertEquals(12345, config.getPort());
        assertEquals(7, config.getVideoRenderWorkerThreads());
        assertFalse(config.isCrawlerProtectionEnabled());
        assertEquals("smtp.example.com", config.getSmtpHost());
    }

    @Test
    @DisplayName("未登记键：不写入配置树，避免管理接口变成任意注入入口")
    void unregisteredKeysAreIgnored() {
        ConfigManager config = new ConfigManager();
        config.applyOverrides(Map.of("mysql.password", "injected"));

        assertEquals("", config.getMysqlPassword());
    }

    @Test
    @DisplayName("非法值：只跳过该条，其余条目仍然生效")
    void invalidValuesAreSkippedIndividually() {
        ConfigManager config = new ConfigManager();
        Map<String, String> overrides = new LinkedHashMap<>();
        overrides.put("port", "not-a-number");
        overrides.put("video_render.worker_threads", "9");

        config.applyOverrides(overrides);

        assertEquals(65535, config.getPort(), "非法值不应改写端口");
        assertEquals(9, config.getVideoRenderWorkerThreads());
    }

    @Test
    @DisplayName("列表项：按行拆成数组，读取端还原为逗号分隔")
    void listValuesBecomeArrays() {
        ConfigManager config = new ConfigManager();
        config.applyOverrides(Map.of("whitelist_email", "a.com\nb.com"));

        assertEquals("a.com,b.com", config.getEmailWhitelist());
        assertEquals("a.com\nb.com", config.effectiveValue("whitelist_email").orElseThrow());
    }

    @Test
    @DisplayName("effectiveValue：键缺失返回 empty，供首次启动回退到出厂默认值")
    void effectiveValueIsEmptyForMissingKeys() {
        ConfigManager config = new ConfigManager();

        assertTrue(config.effectiveValue("smtp.host").isEmpty());
        assertEquals("", config.effectiveValue("smtp.host").orElse(""));
    }

    @Test
    @DisplayName("数值留空：回落到出厂默认值，而不是被当成 0 写进配置树")
    void blankNumericFallsBackToDefault() {
        ConfigManager config = new ConfigManager();
        config.applyOverrides(Map.of("port", "12345"));
        assertEquals(12345, config.getPort());

        config.applyOverrides(Map.of("port", ""));
        assertEquals(65535, config.getPort(), "端口留空应回到默认端口，而不是随机端口");
    }

    @Test
    @DisplayName("数值留空且无出厂默认值：摘掉该键，读取端走自己的缺省分支")
    void blankNumericWithoutDefaultIsRemoved() {
        ConfigManager config = new ConfigManager();
        config.applyOverrides(Map.of("netease_search_fill.upload_user_id", "42"));
        assertEquals(42, config.getNeteaseFillUploadUserId());

        config.applyOverrides(Map.of("netease_search_fill.upload_user_id", ""));
        assertTrue(config.effectiveValue("netease_search_fill.upload_user_id").isEmpty());
    }

    @Test
    @DisplayName("重复叠加：后一次的值覆盖前一次，不影响未提交的键")
    void laterOverridesWin() {
        ConfigManager config = new ConfigManager();
        config.applyOverrides(Map.of("port", "10000", "smtp.host", "first.example.com"));
        config.applyOverrides(Map.of("port", "10001"));

        assertEquals(10001, config.getPort());
        assertEquals("first.example.com", config.getSmtpHost(), "未提交的键应保留上一次的值");
    }
}
