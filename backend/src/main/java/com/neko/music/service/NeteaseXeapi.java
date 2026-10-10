package com.neko.music.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * 网易云 xeapi（反爬加密）移植，与 SPlayer-Next / ArchoeraMusic 的 {@code core/crypto.ts} 对齐。
 *
 * <p>用于游客注册（{@code /xeapi/register/anonimous}）等反爬接口：先用 X25519 ECDH 封装动态密钥，
 * 再用 AES-ECB 分层加密请求体，响应体用 eapi 密钥解密。</p>
 */
final class NeteaseXeapi {

    /** xeapi 固定对称密钥（AES-256-ECB）。 */
    private static final byte[] STATIC_KEY =
            hex("ab1d5a430f6bb04a3f01e81ddd72bd916d5ce591248ac128714806d7f8fb1b84");
    /** xeapi 签名密钥（HMAC-SHA256，按字符串原样作为 key）。 */
    private static final byte[] SIGN_KEY = ("mUHCwVNWJbunMqAHf5MImuirT6plvs6VSFW62MGHstFQxhBGdEoIhLItH3djc4+FB/OKty3+lL2rGeoFBpVe5g==")
            .getBytes(StandardCharsets.UTF_8);
    /** X25519 裸公钥的 RFC 8410 SPKI 前缀。 */
    private static final byte[] X25519_SPKI_PREFIX = hex("302a300506032b656e032100");
    /** eapi 响应解密密钥。 */
    private static final byte[] EAPI_KEY = "e82ckenh8dichen8".getBytes(StandardCharsets.UTF_8);
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 反爬接口下发的 X25519 公钥状态。 */
    record Key(String version, String publicKey, String sk) {}
    /** xeapi 加密结果（B / S / R 三段 base64）。 */
    record Encrypted(String b, String s, String r) {}
    /** 服务端经响应头下发的会话密钥。 */
    record Session(String id, String key) {}

    private NeteaseXeapi() {
    }

    /** 反爬签名：HMAC-SHA256(signKey, timestamp + nonce) → base64。 */
    static String sign(String timestamp, String nonce) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SIGN_KEY, "HmacSHA256"));
            return Base64.getEncoder().encodeToString(
                    mac.doFinal((timestamp + nonce).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("xeapi 签名失败", e);
        }
    }

    /** 解密反爬接口返回的公钥包（AES-ECB(STATIC_KEY) → JSON）。 */
    static Key decryptKey(String encryptedData, ObjectMapper mapper) throws IOException {
        try {
            JsonNode node = mapper.readTree(aesEcbDecrypt(STATIC_KEY, Base64.getDecoder().decode(encryptedData)));
            return new Key(text(node, "version"), text(node, "publicKey"), text(node, "sk"));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("xeapi 公钥解密失败", e);
        }
    }

    /** 构造 xeapi 加密体。 */
    static Encrypted encrypt(String uri, Map<String, Object> data, Key key, Session session, String os) {
        byte[] activeSessionKey = session == null || session.key() == null || session.key().isEmpty()
                ? null : session.key().getBytes(StandardCharsets.UTF_8);
        String activeSessionId = session == null || session.id() == null ? "" : session.id();
        byte[] dynamicKey = activeSessionKey != null ? activeSessionKey : randomBytes(16);

        byte[] plaintext = buildPlaintext(uri, data).getBytes(StandardCharsets.UTF_8);
        byte[] b = aesEcbEncrypt(dynamicKey, midTransform(aesEcbEncrypt(STATIC_KEY, plaintext)));
        byte[] s = encryptS(dynamicKey, key, os);
        byte[] r = aesEcbEncrypt(STATIC_KEY,
                ((key.version() == null ? "" : key.version()) + "|" + (activeSessionKey != null ? activeSessionId : ""))
                        .getBytes(StandardCharsets.UTF_8));
        return new Encrypted(base64(b), base64(s), base64(r));
    }

    /** 解密 xeapi 响应体（AES-ECB(EAPI_KEY) + 可选 gunzip → JSON）。 */
    static JsonNode decryptResponse(byte[] body, ObjectMapper mapper) throws IOException {
        try {
            byte[] decrypted = aesEcbDecrypt(EAPI_KEY, body);
            byte[] plaintext = decrypted.length >= 2
                    && (decrypted[0] & 0xff) == 0x1f && (decrypted[1] & 0xff) == 0x8b
                    ? gunzip(decrypted) : decrypted;
            return mapper.readTree(plaintext);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("xeapi 响应解密失败", e);
        }
    }

    /** xeapi 明文：{@code {"body":"<base64>","queryString":"e_r=true"}}（POST 表单）。 */
    private static String buildPlaintext(String uri, Map<String, Object> data) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (data != null && !data.isEmpty()) {
            Map<String, Object> body = new LinkedHashMap<>(data);
            body.remove("e_r");
            fields.put("body", base64(urlEncode(body).getBytes(StandardCharsets.UTF_8)));
        }
        fields.put("queryString", "e_r=true");
        return toJson(fields);
    }

    /** 变长密钥 AES-ECB 加密（16 → AES-128，32 → AES-256）。 */
    private static byte[] aesEcbEncrypt(byte[] key, byte[] plain) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
            return cipher.doFinal(plain);
        } catch (Exception e) {
            throw new IllegalStateException("xeapi AES 加密失败", e);
        }
    }

    private static byte[] aesEcbDecrypt(byte[] key, byte[] ciphertext) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
        return cipher.doFinal(ciphertext);
    }

    /** 中间层变换：随机 XOR → base64 → 随机旋转。 */
    private static byte[] midTransform(byte[] ciphertext) {
        byte[] random = randomBytes(16);
        byte[] xored = new byte[ciphertext.length];
        for (int i = 0; i < ciphertext.length; i++) {
            xored[i] = (byte) (ciphertext[i] ^ random[i & 0x0f]);
        }
        byte[] b64 = Base64.getEncoder().encodeToString(xored).getBytes(StandardCharsets.UTF_8);
        int rot = b64.length > 0 ? (random[0] & 0x0f) % b64.length : 0;
        return concat(random, Arrays.copyOfRange(b64, rot, b64.length), Arrays.copyOfRange(b64, 0, rot));
    }

    /** 用法：X25519 ECDH + HKDF → AES-128-GCM 封装动态密钥（S 字段）。 */
    private static byte[] encryptS(byte[] dynamicKey, Key key, String os) {
        try {
            PublicKey peer = KeyFactory.getInstance("X25519").generatePublic(
                    new X509EncodedKeySpec(concat(X25519_SPKI_PREFIX, Base64.getDecoder().decode(key.publicKey()))));
            KeyPair keyPair = KeyPairGenerator.getInstance("X25519").generateKeyPair();
            byte[] encoded = keyPair.getPublic().getEncoded();
            byte[] ephemeralRaw = Arrays.copyOfRange(encoded, encoded.length - 32, encoded.length);

            KeyAgreement agreement = KeyAgreement.getInstance("X25519");
            agreement.init(keyPair.getPrivate());
            agreement.doPhase(peer, true);
            byte[] shared = agreement.generateSecret();
            byte[] aesKey = deriveKey(shared, ephemeralRaw);

            byte[] iv = randomBytes(12);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(128, iv));
            byte[] plaintext = (base64(dynamicKey) + "|" + os + "|" + (key.sk() == null ? "" : key.sk()))
                    .getBytes(StandardCharsets.UTF_8);
            byte[] ciphertextWithTag = cipher.doFinal(plaintext);
            return concat(ephemeralRaw, iv, ciphertextWithTag);
        } catch (Exception e) {
            throw new IllegalStateException("xeapi S 字段加密失败", e);
        }
    }

    /** HKDF 风格派生 16 字节 AES 密钥。 */
    private static byte[] deriveKey(byte[] shared, byte[] ephemeral) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(new byte[32], "HmacSHA256"));
        byte[] prk = mac.doFinal(shared.length > 0 ? shared : new byte[32]);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        return Arrays.copyOf(mac.doFinal(concat(ephemeral, new byte[]{1})), 16);
    }

    private static byte[] gunzip(byte[] data) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return gzip.readAllBytes();
        }
    }

    private static String urlEncode(Map<String, Object> values) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!result.isEmpty()) result.append('&');
            result.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
        }
        return result.toString();
    }

    private static String toJson(Map<String, Object> values) {
        StringBuilder result = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!first) result.append(',');
            first = false;
            result.append('"').append(entry.getKey()).append("\":\"")
                    .append(String.valueOf(entry.getValue()).replace("\\", "\\\\").replace("\"", "\\\""))
                    .append('"');
        }
        return result.append('}').toString();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    private static byte[] randomBytes(int n) {
        byte[] bytes = new byte[n];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) length += part.length;
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }

    private static byte[] hex(String value) {
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }
}
