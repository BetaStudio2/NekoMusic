package com.neko.music.model;

/**
 * 客户端应用登记（{@code client_app} 表）。
 *
 * <p>{@code clientId} 是公开标识，不能单独用于认证；真正的身份来自实例私钥、短期实例令牌与
 * 每次请求签名。机密客户端额外持有只在创建时返回一次的服务端 secret。</p>
 */
public record ClientApp(
        long id,
        String clientId,
        String name,
        String ownerLabel,
        String platform,
        boolean confidential,
        String secretHash,
        String status,
        boolean official,
        boolean attestationRequired,
        String allowedOrigins,
        String packageName,
        String signingDigest,
        long createdAt,
        long updatedAt,
        Long expiresAt) {

    public boolean active() {
        return "active".equals(status)
                && (expiresAt == null || expiresAt == 0L || expiresAt > System.currentTimeMillis());
    }

}
