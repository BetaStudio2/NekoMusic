package com.neko.music.service;

/**
 * 用户管理中的可预期失败。携带稳定的文案键，由 Handler 映射为 HTTP 状态码。
 */
public final class UserManagementException extends Exception {

    public enum Kind {
        VALIDATION,
        NOT_FOUND,
        CONFLICT,
        FORBIDDEN,
        INTERNAL
    }

    private final Kind kind;
    private final String messageKey;

    private UserManagementException(Kind kind, String messageKey, Throwable cause) {
        super(messageKey, cause);
        this.kind = kind;
        this.messageKey = messageKey;
    }

    public static UserManagementException validation(String messageKey) {
        return new UserManagementException(Kind.VALIDATION, messageKey, null);
    }

    public static UserManagementException notFound(String messageKey) {
        return new UserManagementException(Kind.NOT_FOUND, messageKey, null);
    }

    public static UserManagementException conflict(String messageKey) {
        return new UserManagementException(Kind.CONFLICT, messageKey, null);
    }

    public static UserManagementException forbidden(String messageKey) {
        return new UserManagementException(Kind.FORBIDDEN, messageKey, null);
    }

    public static UserManagementException internal(String messageKey, Throwable cause) {
        return new UserManagementException(Kind.INTERNAL, messageKey, cause);
    }

    public Kind kind() {
        return kind;
    }

    public String messageKey() {
        return messageKey;
    }
}
