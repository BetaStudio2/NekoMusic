package com.neko.music.model;

/** 某个客户端安装实例的登记信息（{@code client_instance} 表）。 */
public record ClientInstance(
        long id,
        String instanceId,
        String clientId,
        String platform,
        String publicKeySpki,
        String attestationLevel,
        String appId,
        String signingDigest,
        String status,
        long createdAt,
        long lastSeenAt,
        Long expiresAt) {

    public boolean active() {
        return "active".equals(status)
                && (expiresAt == null || expiresAt == 0L || expiresAt > System.currentTimeMillis());
    }
}
