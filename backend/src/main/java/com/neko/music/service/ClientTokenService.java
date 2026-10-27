package com.neko.music.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.neko.music.config.ConfigManager;
import com.neko.music.util.ClientIdentityProtocol;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 短期实例令牌：服务端无状态 HMAC 令牌，避免每个动态请求多一次 Redis / MySQL 读取。
 *
 * <p>令牌只证明「服务端曾认可这个实例」，请求本身仍必须由该实例的 EC 私钥逐次签名。
 * 服务端重启不会让令牌失效；{@code jwt.secret} 轮换会一次性吊销全部旧令牌。</p>
 */
public final class ClientTokenService {

    public static final int TOKEN_TTL_SECONDS = 7 * 24 * 60 * 60;
    private final byte[] signingKey;

    public ClientTokenService(ConfigManager configManager) {
        String secret = configManager == null ? "" : configManager.getJwtSecret();
        this.signingKey = ClientIdentityProtocol.sha256(
                ("neko-client-token-v1:" + (secret == null ? "" : secret))
                        .getBytes(StandardCharsets.UTF_8));
    }

    /** 测试用：直接提供派生后的密钥。 */
    ClientTokenService(byte[] key) {
        this.signingKey = key.clone();
    }

    public record Claims(String clientId, String instanceId, String platform, long expiresAtSeconds) {
        public boolean expired(long nowSeconds) {
            return expiresAtSeconds <= nowSeconds;
        }
    }

    public String issue(String clientId, String instanceId, String platform, long nowSeconds) {
        JsonObject payload = new JsonObject();
        payload.addProperty("v", 1);
        payload.addProperty("cid", clientId);
        payload.addProperty("iid", instanceId);
        payload.addProperty("plat", platform);
        payload.addProperty("iat", nowSeconds);
        payload.addProperty("exp", nowSeconds + TOKEN_TTL_SECONDS);
        String encoded = ClientIdentityProtocol.base64Url(
                payload.toString().getBytes(StandardCharsets.UTF_8));
        String signature = ClientIdentityProtocol.base64Url(
                ClientIdentityProtocol.hmacSha256(signingKey, encoded));
        return encoded + "." + signature;
    }

    /** 校验签名与字段；失败返回 {@code null}。 */
    public Claims verify(String token, long nowSeconds) {
        if (token == null || token.length() > 2048) {
            return null;
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot != token.lastIndexOf('.') || dot == token.length() - 1) {
            return null;
        }
        String encoded = token.substring(0, dot);
        String suppliedSignature = token.substring(dot + 1);
        String expected = ClientIdentityProtocol.base64Url(
                ClientIdentityProtocol.hmacSha256(signingKey, encoded));
        if (!MessageDigest.isEqual(suppliedSignature.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8))) {
            return null;
        }
        try {
            JsonObject payload = JsonParser.parseString(new String(
                    ClientIdentityProtocol.decodeBase64Url(encoded), StandardCharsets.UTF_8)).getAsJsonObject();
            if (payload.get("v").getAsInt() != 1) {
                return null;
            }
            String clientId = payload.get("cid").getAsString();
            String instanceId = payload.get("iid").getAsString();
            String platform = payload.get("plat").getAsString();
            long expires = payload.get("exp").getAsLong();
            if (clientId.isBlank() || instanceId.isBlank() || platform.isBlank()) {
                return null;
            }
            return new Claims(clientId, instanceId, platform, expires);
        } catch (Exception e) {
            return null;
        }
    }
}
