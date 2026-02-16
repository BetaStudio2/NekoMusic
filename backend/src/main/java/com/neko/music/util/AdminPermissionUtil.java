package com.neko.music.util;

import com.neko.music.model.Admin;

/**
 * 管理员权限工具类
 * 用于验证管理员是否有权限执行特定操作
 */
public class AdminPermissionUtil {
    
    /**
     * 角色枚举
     */
    public enum Role {
        SUPER_ADMIN("super_admin"),
        ADMIN("admin"),
        AUDITOR("auditor");
        
        private final String value;
        
        Role(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
        
        public static Role fromString(String value) {
            for (Role role : Role.values()) {
                if (role.value.equals(value)) {
                    return role;
                }
            }
            return ADMIN; // 默认为管理员
        }
    }
    
    /**
     * 权限枚举
     */
    public enum Permission {
        // 审核相关
        AUDIT_VIEW,
        AUDIT_APPROVE,
        AUDIT_REJECT,
        
        // 音乐管理相关
        MUSIC_VIEW,
        MUSIC_ADD,
        MUSIC_EDIT,
        MUSIC_DELETE,
        
        // 用户管理相关
        USER_VIEW,
        USER_EDIT,
        USER_DELETE,
        
        // 管理员管理相关（仅超级管理员）
        ADMIN_VIEW,
        ADMIN_ADD,
        ADMIN_EDIT,
        ADMIN_DELETE,
        ADMIN_CHANGE_PASSWORD,
        
        // 统计数据相关
        STATS_VIEW
    }
    
    /**
     * 检查管理员是否有指定权限
     * @param admin 管理员对象
     * @param permission 权限
     * @return 是否有权限
     */
    public static boolean hasPermission(Admin admin, Permission permission) {
        if (admin == null || !admin.isActive()) {
            return false;
        }
        
        Role role = Role.fromString(admin.getRole());
        
        switch (role) {
            case SUPER_ADMIN:
                // 超级管理员拥有所有权限
                return true;
                
            case ADMIN:
                // 管理员拥有除管理员管理外的所有权限
                switch (permission) {
                    case ADMIN_VIEW:
                    case ADMIN_ADD:
                    case ADMIN_EDIT:
                    case ADMIN_DELETE:
                    case ADMIN_CHANGE_PASSWORD:
                        return false;
                    default:
                        return true;
                }
                
            case AUDITOR:
                // 审核员只能查看和审核，不能修改或删除
                switch (permission) {
                    case AUDIT_VIEW:
                    case AUDIT_APPROVE:
                    case AUDIT_REJECT:
                    case STATS_VIEW:
                        return true;
                    default:
                        return false;
                }
                
            default:
                return false;
        }
    }
    
    /**
     * 检查管理员是否为超级管理员
     * @param admin 管理员对象
     * @return 是否为超级管理员
     */
    public static boolean isSuperAdmin(Admin admin) {
        return admin != null && Role.SUPER_ADMIN.getValue().equals(admin.getRole());
    }
    
    /**
     * 检查管理员是否为管理员
     * @param admin 管理员对象
     * @return 是否为管理员
     */
    public static boolean isAdmin(Admin admin) {
        return admin != null && Role.ADMIN.getValue().equals(admin.getRole());
    }
    
    /**
     * 检查管理员是否为审核员
     * @param admin 管理员对象
     * @return 是否为审核员
     */
    public static boolean isAuditor(Admin admin) {
        return admin != null && Role.AUDITOR.getValue().equals(admin.getRole());
    }
}