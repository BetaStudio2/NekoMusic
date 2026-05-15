package com.neko.music.payment.zpay;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * ZPay / 易支付 MD5 签名：参数名 ASCII 升序，排除 sign、sign_type 与空值，拼接后与商户 KEY 直接相连再 MD5（小写）。
 *
 * @see <a href="https://z-pay.cn/doc.html">ZPay 开发文档</a>
 */
public final class ZpaySignUtil {
    private ZpaySignUtil() {
    }

    public static String buildSignSource(Map<String, String> params) {
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            String k = e.getKey();
            String v = e.getValue();
            if (k == null || v == null) {
                continue;
            }
            if ("sign".equalsIgnoreCase(k) || "sign_type".equalsIgnoreCase(k)) {
                continue;
            }
            if (v.isEmpty()) {
                continue;
            }
            sorted.put(k, v);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    public static String md5LowerHex(String plain) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(plain.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    }

    /** 待签名字符串 + 商户密钥（无额外分隔符） */
    public static String sign(Map<String, String> params, String merchantKey) {
        return md5LowerHex(buildSignSource(params) + merchantKey);
    }

    public static boolean verify(Map<String, String> params, String merchantKey, String receivedSign) {
        if (receivedSign == null || receivedSign.isEmpty()) {
            return false;
        }
        return sign(params, merchantKey).equalsIgnoreCase(receivedSign.trim());
    }
}
