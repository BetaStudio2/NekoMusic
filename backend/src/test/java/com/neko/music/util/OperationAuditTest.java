package com.neko.music.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 操作日志「改了什么」的文本拼装。
 *
 * <p>这一段决定后台日志页上看得见的东西：拼错了要么读不懂，要么把一条变更拆成好几句。
 * 三种最容易踩的坑——多行取值（列表型配置自带换行）、空白值、超长取值——都在这里钉住。</p>
 */
class OperationAuditTest {

    private static OperationAudit.Change change(String label, String before, String after) {
        return new OperationAudit.Change(label, before, after);
    }

    @Test
    @DisplayName("没有明细时返回 null，交给过滤器退回路径目标")
    void noChangesYieldsNull() {
        assertNull(OperationAudit.describe((List<OperationAudit.Change>) null));
        assertNull(OperationAudit.describe(List.of()));
    }

    @Test
    @DisplayName("一条变更：标签：原值 → 新值")
    void formatsSingleChange() {
        assertEquals("运行端口：65535 → 8080",
                OperationAudit.describe(List.of(change("运行端口", "65535", "8080"))));
    }

    @Test
    @DisplayName("多条变更一行一条，顺序与提交顺序一致")
    void formatsMultipleChanges() {
        String detail = OperationAudit.describe(List.of(
                change("启用 IP 频率限制", "true", "false"),
                change("窗口内最大请求数", "300", "500")));
        assertEquals("启用 IP 频率限制：true → false\n窗口内最大请求数：300 → 500", detail);
    }

    @Test
    @DisplayName("多行取值压成一行：换行是条目分隔符，不能被取值里的换行污染")
    void flattensMultilineValues() {
        String detail = OperationAudit.describe(List.of(
                change("注册邮箱域名白名单", "qq.com\ngmail.com", "qq.com\ngmail.com\noutlook.com"),
                change("窗口内最大请求数", "300", "500")));
        assertEquals("注册邮箱域名白名单：qq.com\\ngmail.com → qq.com\\ngmail.com\\noutlook.com"
                + "\n窗口内最大请求数：300 → 500", detail);
    }

    @Test
    @DisplayName("空值显示为（空），不出现读不懂的空箭头")
    void blanksAreReadable() {
        assertEquals("SMTP 主机：（空） → smtp.qq.com",
                OperationAudit.describe(List.of(change("SMTP 主机", null, "smtp.qq.com"))));
        assertEquals("SMTP 主机：smtp.qq.com → （空）",
                OperationAudit.describe(List.of(change("SMTP 主机", "smtp.qq.com", "   "))));
    }

    @Test
    @DisplayName("超长取值截断，免得一条明细吃掉整列")
    void truncatesLongValues() {
        String longValue = "x".repeat(400);
        String detail = OperationAudit.describe(List.of(change("白名单", longValue, "q")));
        assertTrue(detail.startsWith("白名单：" + "x".repeat(120) + "… → q"), detail);
    }

    @Test
    @DisplayName("明细超长时省略后续条目，但写明一共改了几项")
    void truncatesChangeList() {
        List<OperationAudit.Change> changes = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            changes.add(change("第" + i + "项设置", "a".repeat(100), "b".repeat(100)));
        }
        String detail = OperationAudit.describe(changes);
        assertTrue(detail.length() <= 1100, "明细长度应在列宽预算内: " + detail.length());
        assertTrue(detail.contains("共 60 项，其余省略"), detail);
    }

    @Test
    @DisplayName("敏感项的状态迁移文案原样保留（值由处理器脱敏，不经这里）")
    void keepsHandlerProvidedStateText() {
        assertEquals("SMTP 密码：已配置 → 已更新（不回显）",
                OperationAudit.describe(List.of(change("SMTP 密码", "已配置", "已更新（不回显）"))));
    }
}
