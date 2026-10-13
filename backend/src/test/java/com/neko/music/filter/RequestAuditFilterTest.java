package com.neko.music.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 操作日志的模块 / 动作归类：后台检索全靠它，不能把「用户管理」记成「管理后台」。 */
class RequestAuditFilterTest {

    @Test
    @DisplayName("模块按最长前缀归类")
    void resolvesModuleByLongestPrefix() {
        assertEquals("用户管理", RequestAuditFilter.resolveModule("/api/admin/users/12"));
        assertEquals("歌词管理", RequestAuditFilter.resolveModule("/api/admin/lyrics-files/9"));
        assertEquals("VIP 定价", RequestAuditFilter.resolveModule("/api/admin/vip/pricing"));
        assertEquals("审核管理", RequestAuditFilter.resolveModule("/api/admin/audit/pending"));
        assertEquals("管理后台", RequestAuditFilter.resolveModule("/api/admin/unknown"));
    }

    @Test
    @DisplayName("登录 / 登出单列，其余按方法归纳")
    void resolvesAction() {
        assertEquals("登录", RequestAuditFilter.resolveAction("/api/admin/login", "POST"));
        assertEquals("登出", RequestAuditFilter.resolveAction("/api/admin/logout", "POST"));
        assertEquals("提交", RequestAuditFilter.resolveAction("/api/admin/users", "POST"));
        assertEquals("更新", RequestAuditFilter.resolveAction("/api/admin/settings", "PUT"));
        assertEquals("删除", RequestAuditFilter.resolveAction("/api/admin/users/1", "DELETE"));
    }
}
