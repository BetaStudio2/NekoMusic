package com.neko.music.handlers;

import com.neko.music.util.OperationAudit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「修改系统设置」到底改了什么。
 *
 * <p>后台操作日志里最有价值的一条就是它：只写「修改系统设置」等于没记。这里钉住三件事——非敏感项
 * 要写出「原值 → 新值」、没变的项不能凭空记一条、敏感项绝不能把明文写进日志库。</p>
 */
class AdminSettingsHandlerTest {

    @Test
    @DisplayName("非敏感项：写出标签与「原值 → 新值」")
    void recordsBeforeAndAfter() {
        List<OperationAudit.Change> changes = AdminSettingsHandler.settingChanges(
                Map.of("rate_limit.max_requests", "300"),
                Map.of("rate_limit.max_requests", "500"));
        assertEquals(List.of(new OperationAudit.Change("窗口内最大请求数", "300", "500")), changes);
    }

    @Test
    @DisplayName("原值取当前生效值（后台页面上显示的那个），不是提交值本身")
    void beforeIsEffectiveValue() {
        String detail = OperationAudit.describe(AdminSettingsHandler.settingChanges(
                Map.of("smtp.host", "smtp.old.com", "rate_limit.enabled", "true"),
                Map.of("smtp.host", "smtp.qq.com", "rate_limit.enabled", "false")));
        assertTrue(detail.contains("SMTP 主机：smtp.old.com → smtp.qq.com"), detail);
        assertTrue(detail.contains("启用 IP 频率限制：true → false"), detail);
    }

    @Test
    @DisplayName("值没变就不记：前端可能提交了改动过又改回去的键")
    void skipsUnchangedValues() {
        assertTrue(AdminSettingsHandler.settingChanges(
                Map.of("rate_limit.max_requests", "300"),
                Map.of("rate_limit.max_requests", "300")).isEmpty());
    }

    @Test
    @DisplayName("敏感项不落明文，只记状态迁移")
    void neverLogsSecretPlaintext() {
        LinkedHashMap<String, String> stored = new LinkedHashMap<>();
        stored.put("smtp.password", "");
        LinkedHashMap<String, String> updates = new LinkedHashMap<>();
        updates.put("smtp.password", "s3cr3t-token");

        List<OperationAudit.Change> changes = AdminSettingsHandler.settingChanges(stored, updates);
        assertEquals(List.of(new OperationAudit.Change("SMTP 密码", "未配置", "已配置")), changes);
        String detail = OperationAudit.describe(changes);
        assertTrue(!detail.contains("s3cr3t"), detail);

        // 已配置 → 再次提交：值不可比，只写「已更新（不回显）」
        assertEquals(List.of(new OperationAudit.Change("SMTP 密码", "已配置", "已更新（不回显）")),
                AdminSettingsHandler.settingChanges(Map.of("smtp.password", "old-secret"),
                        Map.of("smtp.password", "new-secret")));

        // 提交空串 = 清空
        assertEquals(List.of(new OperationAudit.Change("SMTP 密码", "已配置", "已清空")),
                AdminSettingsHandler.settingChanges(Map.of("smtp.password", "old-secret"),
                        Map.of("smtp.password", "")));
    }

    @Test
    @DisplayName("列表型配置：多行取值压成一行，不把一条变更拆成多行")
    void flattensListValues() {
        List<OperationAudit.Change> changes = AdminSettingsHandler.settingChanges(
                Map.of("whitelist_email", "qq.com\ngmail.com"),
                Map.of("whitelist_email", "qq.com\ngmail.com\noutlook.com"));
        assertEquals("注册邮箱域名白名单：qq.com\\ngmail.com → qq.com\\ngmail.com\\noutlook.com",
                OperationAudit.describe(changes));
    }

    @Test
    @DisplayName("未登记的键不进明细（防御：正常流程在更早的地方就拒绝了）")
    void ignoresUnknownKeys() {
        assertTrue(AdminSettingsHandler.settingChanges(Map.of(), Map.of("not.registered", "x")).isEmpty());
    }
}
