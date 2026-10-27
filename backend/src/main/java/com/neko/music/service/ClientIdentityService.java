package com.neko.music.service;

import com.neko.music.database.ClientRegistryDatabaseManager;
import com.neko.music.model.ClientApp;
import com.neko.music.model.ClientInstance;
import com.neko.music.seo.UserAgentClassifier;
import com.neko.music.util.ClientIdentityProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端实例身份的核心校验与签发。
 *
 * <p>{@code client_id} 是公开应用标识；每个安装实例另有不可导出的 EC P-256 私钥、实例 ID 与短期令牌。
 * 服务端验证「应用启用 + 平台一致 + 实例未吊销 + 令牌未过期 + 时间戳在窗口内 + 请求签名正确」。
 * 每次请求仍由 {@code ReplayProtectionFilter} 消费一次性 nonce，两层互不替代。</p>
 */
public class ClientIdentityService {

    private static final Logger logger = LoggerFactory.getLogger(ClientIdentityService.class);
    private static final long APP_CACHE_TTL_MS = 60_000L;
    private static final long INSTANCE_CACHE_TTL_MS = 30_000L;
    private static final int MAX_CACHE_ENTRIES = 10_000;

    private final ClientRegistryDatabaseManager registry;
    private final ClientTokenService tokenService;
    private final Map<String, CachedApp> appCache = new ConcurrentHashMap<>();
    private final Map<String, CachedInstance> instanceCache = new ConcurrentHashMap<>();
    private volatile long clockMillis = -1L;

    public ClientIdentityService(ClientRegistryDatabaseManager registry, ClientTokenService tokenService) {
        this.registry = registry;
        this.tokenService = tokenService;
    }

    public record Credentials(String clientId, String instanceId, String token, String platform,
                              long timestampSeconds, String signature, String bodyHash, String nonce,
                              String method, String path, String query, String userAgent,
                              String origin, String clientSecret) {
    }

    public record RegistrationInput(String clientId, String platform, String instanceId,
                                    String publicKeySpki, String challenge, long timestampSeconds,
                                    String signature, List<String> attestationChain,
                                    String origin) {
    }

    public record TokenInput(String clientId, String platform, String instanceId, String challenge,
                             long timestampSeconds, String signature) {
    }

    public record IssuedToken(String token, long expiresAtSeconds) {
    }

    public record Verification(ClientApp app, ClientInstance instance, PublicKey publicKey) {
    }

    public record Registration(ClientInstance instance, IssuedToken token, String attestationLevel) {
    }

    private record CachedApp(ClientApp app, long loadedAt) {
    }

    private record CachedInstance(ClientInstance instance, long loadedAt) {
    }

    public Optional<ClientApp> findApp(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return Optional.empty();
        }
        long now = nowMillis();
        CachedApp cached = appCache.get(clientId);
        if (cached != null && now - cached.loadedAt() < APP_CACHE_TTL_MS) {
            return Optional.of(cached.app());
        }
        Optional<ClientApp> loaded = registry.findApp(clientId);
        loaded.ifPresent(app -> putBounded(appCache, clientId, new CachedApp(app, now)));
        return loaded;
    }

    public Optional<ClientInstance> findInstance(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            return Optional.empty();
        }
        long now = nowMillis();
        CachedInstance cached = instanceCache.get(instanceId);
        if (cached != null && now - cached.loadedAt() < INSTANCE_CACHE_TTL_MS) {
            return Optional.of(cached.instance());
        }
        Optional<ClientInstance> loaded = registry.findInstance(instanceId);
        loaded.ifPresent(instance -> putBounded(instanceCache, instanceId,
                new CachedInstance(instance, now)));
        return loaded;
    }

    public void invalidate(String clientId, String instanceId) {
        if (clientId != null) {
            appCache.remove(clientId);
        }
        if (instanceId != null) {
            instanceCache.remove(instanceId);
        }
        if (clientId == null && instanceId == null) {
            appCache.clear();
            instanceCache.clear();
        }
    }

    /** 注册新实例。返回空表示输入 / 证明无效；调用方统一回 401。 */
    public Optional<Registration> register(RegistrationInput input, String clientIp, String userAgent,
                                           String suppliedSecret) {
        if (input == null || !ClientIdentityProtocol.isInstanceId(input.instanceId())
                || !ClientIdentityProtocol.isPlatform(input.platform())
                || input.clientId() == null || input.clientId().isBlank()
                || input.challenge() == null || input.challenge().isBlank()
                || input.publicKeySpki() == null || input.publicKeySpki().isBlank()
                || !withinTimeWindow(input.timestampSeconds())) {
            return Optional.empty();
        }
        Optional<ClientApp> appOpt = findApp(input.clientId());
        if (appOpt.isEmpty() || !appOpt.get().active()
                || !input.platform().equals(appOpt.get().platform())) {
            return Optional.empty();
        }
        ClientApp app = appOpt.get();
        if (app.confidential() && !registry.verifySecret(app.clientId(), suppliedSecret)) {
            return Optional.empty();
        }
        if (!validateReportedPlatform(app, userAgent)) {
            return Optional.empty();
        }
        if (!validateOrigin(app, input.origin())) {
            return Optional.empty();
        }

        PublicKey publicKey;
        try {
            publicKey = ClientIdentityProtocol.parseEcPublicKey(input.publicKeySpki());
        } catch (Exception e) {
            return Optional.empty();
        }
        String canonical = ClientIdentityProtocol.canonicalRegister(
                input.clientId(), input.platform(), input.instanceId(),
                input.publicKeySpki(), input.challenge(), input.timestampSeconds());
        if (!ClientIdentityProtocol.verifyEcSignature(publicKey, canonical, input.signature())) {
            return Optional.empty();
        }
        if (ClientChallengeService.consume(input.challenge(), input.clientId(), input.platform(),
                ClientChallengeService.Purpose.REGISTER, clientIp, userAgent)
                != ClientChallengeService.Status.OK) {
            return Optional.empty();
        }

        String attestationLevel = "none";
        String appId = input.origin() == null ? "" : input.origin().trim();
        String signingDigest = "";
        if (app.attestationRequired()) {
            try {
                ClientAttestationVerifier.Result attestation = ClientAttestationVerifier.verify(
                        input.attestationChain(), input.publicKeySpki(), null,
                        app.packageName(), app.signingDigest());
                attestationLevel = attestation.securityLevel();
                appId = attestation.applicationId();
                signingDigest = attestation.signingDigest();
            } catch (RuntimeException e) {
                logger.warn("Android 客户端证明校验失败 clientId={} instanceId={}: {}",
                        input.clientId(), input.instanceId(), e.getMessage());
                return Optional.empty();
            }
        }

        Optional<ClientInstance> existing = registry.findInstance(input.instanceId());
        if (existing.isPresent()) {
            ClientInstance old = existing.get();
            if (!old.active() || !old.clientId().equals(input.clientId())
                    || !old.platform().equals(input.platform())
                    || !old.publicKeySpki().equals(input.publicKeySpki())) {
                return Optional.empty();
            }
        }

        long now = nowMillis();
        ClientInstance instance = new ClientInstance(0L, input.instanceId(), input.clientId(),
                input.platform(), input.publicKeySpki(), attestationLevel,
                appId, signingDigest, "active", now, now, null);
        try {
            if (!registry.registerInstance(instance)) {
                return Optional.empty();
            }
        } catch (Exception e) {
            logger.warn("客户端实例注册失败 clientId={} instanceId={}: {}",
                    input.clientId(), input.instanceId(), e.getMessage());
            return Optional.empty();
        }
        invalidate(input.clientId(), input.instanceId());
        Optional<ClientInstance> stored = registry.findInstance(input.instanceId());
        if (stored.isEmpty() || !stored.get().active()
                || !stored.get().publicKeySpki().equals(input.publicKeySpki())) {
            return Optional.empty();
        }
        String token = tokenService.issue(input.clientId(), input.instanceId(), input.platform(),
                now / 1000L);
        return Optional.of(new Registration(stored.get(),
                new IssuedToken(token, now / 1000L + ClientTokenService.TOKEN_TTL_SECONDS),
                attestationLevel));
    }

    /** 用实例私钥续签短期令牌；不重新登记实例。 */
    public Optional<IssuedToken> refresh(TokenInput input, String clientIp, String userAgent) {
        if (input == null || !ClientIdentityProtocol.isInstanceId(input.instanceId())
                || !ClientIdentityProtocol.isPlatform(input.platform())
                || input.clientId() == null || input.clientId().isBlank()
                || input.challenge() == null || input.challenge().isBlank()
                || !withinTimeWindow(input.timestampSeconds())) {
            return Optional.empty();
        }
        Optional<ClientApp> app = findApp(input.clientId());
        Optional<ClientInstance> instance = findInstance(input.instanceId());
        if (app.isEmpty() || instance.isEmpty() || !app.get().active() || !instance.get().active()
                || !input.clientId().equals(instance.get().clientId())
                || !input.platform().equals(instance.get().platform())
                || !validateReportedPlatform(app.get(), userAgent)) {
            return Optional.empty();
        }
        PublicKey key;
        try {
            key = ClientIdentityProtocol.parseEcPublicKey(instance.get().publicKeySpki());
        } catch (Exception e) {
            return Optional.empty();
        }
        String canonical = ClientIdentityProtocol.canonicalToken(
                input.clientId(), input.platform(), input.instanceId(),
                input.challenge(), input.timestampSeconds());
        if (!ClientIdentityProtocol.verifyEcSignature(key, canonical, input.signature())) {
            return Optional.empty();
        }
        if (ClientChallengeService.consume(input.challenge(), input.clientId(), input.platform(),
                ClientChallengeService.Purpose.TOKEN, clientIp, userAgent)
                != ClientChallengeService.Status.OK) {
            return Optional.empty();
        }
        long nowSeconds = nowMillis() / 1000L;
        registry.touchInstance(input.instanceId());
        invalidate(input.clientId(), input.instanceId());
        return Optional.of(new IssuedToken(tokenService.issue(
                input.clientId(), input.instanceId(), input.platform(), nowSeconds),
                nowSeconds + ClientTokenService.TOKEN_TTL_SECONDS));
    }

    /** 校验动态请求。失败原因只进调试日志，对客户端统一 401。 */
    public Optional<Verification> verify(Credentials credentials) {
        if (credentials == null
                || credentials.clientId() == null || credentials.clientId().isBlank()
                || credentials.instanceId() == null || credentials.instanceId().isBlank()
                || credentials.token() == null || credentials.token().isBlank()
                || credentials.platform() == null || credentials.platform().isBlank()
                || credentials.signature() == null || credentials.signature().isBlank()
                || credentials.bodyHash() == null || credentials.bodyHash().isBlank()
                || !withinTimeWindow(credentials.timestampSeconds())) {
            return Optional.empty();
        }
        long nowSeconds = nowMillis() / 1000L;
        ClientTokenService.Claims claims = tokenService.verify(credentials.token(), nowSeconds);
        if (claims == null || claims.expired(nowSeconds)
                || !claims.clientId().equals(credentials.clientId())
                || !claims.instanceId().equals(credentials.instanceId())
                || !claims.platform().equals(credentials.platform())) {
            return Optional.empty();
        }
        Optional<ClientApp> appOpt = findApp(credentials.clientId());
        Optional<ClientInstance> instanceOpt = findInstance(credentials.instanceId());
        if (appOpt.isEmpty() || instanceOpt.isEmpty()) {
            return Optional.empty();
        }
        ClientApp app = appOpt.get();
        ClientInstance instance = instanceOpt.get();
        if (!app.active() || !instance.active()
                || !app.platform().equals(credentials.platform())
                || !instance.platform().equals(credentials.platform())
                || !instance.clientId().equals(credentials.clientId())
                || !validateReportedPlatform(app, credentials.userAgent())) {
            return Optional.empty();
        }
        PublicKey key;
        try {
            key = ClientIdentityProtocol.parseEcPublicKey(instance.publicKeySpki());
        } catch (Exception e) {
            return Optional.empty();
        }
        String canonical = ClientIdentityProtocol.canonicalRequest(
                credentials.method(), credentials.path(), credentials.query(),
                credentials.clientId(), credentials.instanceId(), credentials.platform(),
                credentials.timestampSeconds(), credentials.bodyHash(), credentials.nonce());
        if (!ClientIdentityProtocol.verifyEcSignature(key, canonical, credentials.signature())) {
            return Optional.empty();
        }
        return Optional.of(new Verification(app, instance, key));
    }

    public boolean validateReportedPlatform(ClientApp app, String userAgent) {
        String ua = userAgent == null ? "" : userAgent.trim();
        UserAgentClassifier.Profile profile = UserAgentClassifier.profile(ua);
        if (profile.bot()) {
            return false;
        }
        if (!app.official()) {
            return true;
        }
        return switch (app.platform()) {
            case "web" -> profile.browser() && !profile.nativeClient();
            case "android" -> "android".equals(profile.platform());
            case "androidwatch" -> "awatch".equals(profile.platform());
            case "pc" -> "pc".equals(profile.platform());
            default -> true;
        };
    }

    private boolean validateOrigin(ClientApp app, String origin) {
        if (!"web".equals(app.platform()) || app.allowedOrigins() == null
                || app.allowedOrigins().isBlank()) {
            return true;
        }
        if (origin == null || origin.isBlank()) {
            return false;
        }
        String normalized = origin.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(app.allowedOrigins().split("[,\\n]"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .anyMatch(value -> value.toLowerCase(Locale.ROOT).equals(normalized));
    }

    private boolean withinTimeWindow(long timestampSeconds) {
        long now = nowMillis() / 1000L;
        return Math.abs(now - timestampSeconds) <= ClientIdentityProtocol.REQUEST_TIME_WINDOW_SECONDS;
    }

    private long nowMillis() {
        long configured = clockMillis;
        return configured < 0 ? System.currentTimeMillis() : configured;
    }

    private static <T> void putBounded(Map<String, T> cache, String key, T value) {
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.clear();
        }
        cache.put(key, value);
    }

    /** 测试用：固定时钟。 */
    void useClock(long millis) {
        clockMillis = millis;
    }

    /** 生成仅供后台创建客户端时使用的随机公开 ID。 */
    public static String newClientId(boolean confidential) {
        String raw = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        return (confidential ? "nkc_sec_" : "nkc_pub_") + raw;
    }

    /** 生成机密客户端 secret（创建时返回一次，数据库只存哈希）。 */
    public static String newClientSecret() {
        byte[] raw = new byte[32];
        new java.security.SecureRandom().nextBytes(raw);
        return ClientIdentityProtocol.base64Url(raw);
    }

    static byte[] utf8(String value) {
        return value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
    }
}
