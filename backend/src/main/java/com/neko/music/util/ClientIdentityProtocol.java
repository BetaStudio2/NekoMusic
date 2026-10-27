package com.neko.music.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 客户端身份协议 v1 的纯函数部分：请求签名串、base64url、SPKI 与 ECDSA 校验。
 *
 * <p>协议只依赖 JDK 自带的 EC P-256 / SHA-256，客户端私钥始终留在本机（Android Keystore、
 * WebCrypto、PC 系统凭据库或加密文件）。服务端收到的 {@code client_id} 是公开标识，
 * 单独泄露它不能伪造请求。</p>
 */
public final class ClientIdentityProtocol {

    public static final String PROTOCOL_VERSION = "neko-client-v1";

    public static final String HEADER_CLIENT_ID = "X-Neko-Client-Id";
    public static final String HEADER_INSTANCE = "X-Neko-Client-Instance";
    public static final String HEADER_TOKEN = "X-Neko-Client-Token";
    public static final String HEADER_TIMESTAMP = "X-Neko-Client-Timestamp";
    public static final String HEADER_SIGNATURE = "X-Neko-Client-Signature";
    public static final String HEADER_SECRET = "X-Neko-Client-Secret";

    /** 与 URL 中 multipart 的边界无关：流式上传无法在网关层完整重放，因此签名不绑定 body。 */
    public static final String STREAMED_BODY_HASH = "-";

    /** 请求时间窗（秒）。一次性 nonce 负责窗口内的重放，本窗口负责压缩截获后长期利用的空间。 */
    public static final long REQUEST_TIME_WINDOW_SECONDS = 300L;

    public static final int MAX_PUBLIC_KEY_BYTES = 512;
    public static final int MAX_SIGNATURE_BYTES = 256;

    private static final Pattern SELF_REPORTED_CLIENT = Pattern.compile(
            "^([a-z0-9]{2,24})\\+([A-Za-z0-9][A-Za-z0-9._+-]{0,63})$");
    private static final Pattern PLATFORM = Pattern.compile("^[a-z][a-z0-9]{1,23}$");
    private static final Pattern INSTANCE_ID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

    private ClientIdentityProtocol() {
    }

    /** {@code X-Neko-Client: <平台>+<版本>} 的解析结果。 */
    public record ReportedClient(String platform, String version) {
    }

    /** 解析自报客户端；格式不对返回 {@code null}。 */
    public static ReportedClient parseReportedClient(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = SELF_REPORTED_CLIENT.matcher(value.trim());
        if (!matcher.matches()) {
            return null;
        }
        return new ReportedClient(matcher.group(1).toLowerCase(Locale.ROOT), matcher.group(2));
    }

    public static boolean isPlatform(String platform) {
        return platform != null && PLATFORM.matcher(platform).matches();
    }

    public static boolean isInstanceId(String instanceId) {
        return instanceId != null && INSTANCE_ID.matcher(instanceId).matches();
    }

    /** 统一签名串。客户端与服务端必须逐字节一致。 */
    public static String canonicalRequest(String method,
                                          String path,
                                          String query,
                                          String clientId,
                                          String instanceId,
                                          String platform,
                                          long timestampSeconds,
                                          String bodyHash,
                                          String nonce) {
        String normalizedQuery = query == null ? "" : query;
        String normalizedNonce = nonce == null ? "" : nonce;
        return PROTOCOL_VERSION + "\n"
                + upper(method) + "\n"
                + nullToEmpty(path) + "\n"
                + normalizedQuery + "\n"
                + nullToEmpty(clientId) + "\n"
                + nullToEmpty(instanceId) + "\n"
                + nullToEmpty(platform) + "\n"
                + timestampSeconds + "\n"
                + nullToEmpty(bodyHash) + "\n"
                + normalizedNonce;
    }

    /** 注册时证明新实例确实持有对应私钥。 */
    public static String canonicalRegister(String clientId,
                                           String platform,
                                           String instanceId,
                                           String publicKeySpki,
                                           String challenge,
                                           long timestampSeconds) {
        return "neko-client-register-v1\n"
                + nullToEmpty(clientId) + "\n"
                + nullToEmpty(platform) + "\n"
                + nullToEmpty(instanceId) + "\n"
                + nullToEmpty(publicKeySpki) + "\n"
                + nullToEmpty(challenge) + "\n"
                + timestampSeconds;
    }

    /** 短期令牌续签：用实例私钥证明续签者仍是同一实例。 */
    public static String canonicalToken(String clientId,
                                        String platform,
                                        String instanceId,
                                        String challenge,
                                        long timestampSeconds) {
        return "neko-client-token-v1\n"
                + nullToEmpty(clientId) + "\n"
                + nullToEmpty(platform) + "\n"
                + nullToEmpty(instanceId) + "\n"
                + nullToEmpty(challenge) + "\n"
                + timestampSeconds;
    }

    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data == null ? new byte[0] : data));
        } catch (Exception e) {
            throw new IllegalStateException("JDK 不支持 SHA-256", e);
        }
    }

    public static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data == null ? new byte[0] : data);
        } catch (Exception e) {
            throw new IllegalStateException("JDK 不支持 SHA-256", e);
        }
    }

    public static byte[] hmacSha256(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("JDK 不支持 HmacSHA256", e);
        }
    }

    public static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public static byte[] decodeBase64Url(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("empty base64url");
        }
        return Base64.getUrlDecoder().decode(value.trim());
    }

    /** 解析客户端上传的 SPKI DER（base64url），并限制为 EC 公钥。 */
    public static PublicKey parseEcPublicKey(String spkiBase64Url) throws Exception {
        byte[] encoded = decodeBase64Url(spkiBase64Url);
        if (encoded.length == 0 || encoded.length > MAX_PUBLIC_KEY_BYTES) {
            throw new IllegalArgumentException("invalid public key length");
        }
        PublicKey key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(encoded));
        if (!(key instanceof java.security.interfaces.ECPublicKey ec)) {
            throw new IllegalArgumentException("not an EC public key");
        }
        if (ec.getParams().getCurve().getField().getFieldSize() != 256
                || ec.getParams().getOrder().bitLength() != 256
                || ec.getParams().getCofactor() != 1) {
            throw new IllegalArgumentException("EC P-256 is required");
        }
        return key;
    }

    /** 校验 DER 编码的 SHA256withECDSA 签名。 */
    public static boolean verifyEcSignature(PublicKey key, String canonical, String signatureBase64Url) {
        if (key == null || canonical == null || signatureBase64Url == null) {
            return false;
        }
        try {
            byte[] signature = decodeBase64Url(signatureBase64Url);
            if (signature.length == 0 || signature.length > MAX_SIGNATURE_BYTES) {
                return false;
            }
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(key);
            verifier.update(canonical.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(signature);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }

    /** 将 WebCrypto 的 IEEE P1363（r||s）签名转成 Java/OpenSSL 默认的 DER。 */
    public static byte[] p1363ToDer(byte[] signature) {
        if (signature == null || signature.length != 64) {
            throw new IllegalArgumentException("P-256 signature must be 64 bytes");
        }
        byte[] r = trimUnsigned(signature, 0, 32);
        byte[] s = trimUnsigned(signature, 32, 32);
        byte[] sequence = concat(derInteger(r), derInteger(s));
        return concat(new byte[] {0x30, (byte) sequence.length}, sequence);
    }

    private static byte[] derInteger(byte[] value) {
        int offset = 0;
        while (offset < value.length - 1 && value[offset] == 0) {
            offset++;
        }
        byte[] trimmed = java.util.Arrays.copyOfRange(value, offset, value.length);
        if ((trimmed[0] & 0x80) != 0) {
            trimmed = concat(new byte[] {0}, trimmed);
        }
        return concat(new byte[] {0x02, (byte) trimmed.length}, trimmed);
    }

    private static byte[] trimUnsigned(byte[] value, int offset, int length) {
        int end = offset + length;
        while (offset < end - 1 && value[offset] == 0) {
            offset++;
        }
        return java.util.Arrays.copyOfRange(value, offset, end);
    }

    private static byte[] concat(byte[] left, byte[] right) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(left.length + right.length);
        out.writeBytes(left);
        out.writeBytes(right);
        return out.toByteArray();
    }

    private static String upper(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
