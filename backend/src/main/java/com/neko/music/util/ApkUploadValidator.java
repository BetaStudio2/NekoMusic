package com.neko.music.util;

import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.ApkMeta;
import net.dongliu.apk.parser.bean.ApkSigner;
import net.dongliu.apk.parser.bean.ApkV2Signer;
import net.dongliu.apk.parser.bean.CertificateMeta;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 管理员上传 Android APK：校验包名、versionName 与 release 签名证书（常量硬编码） */
public final class ApkUploadValidator {

    /** 与 Android/app/build.gradle.kts applicationId 一致 */
    private static final String EXPECTED_PACKAGE_NAME = "com.neko.music";

    /**
     * release keystore（neko_key.jks）证书 SHA-256，不会轮换。
     * 查看：keytool -list -v -keystore neko_key.jks -alias neko
     */
    private static final String EXPECTED_CERT_SHA256 =
            "53a64cdb1491a2b28a3d309e41e92addbabc691b8fb5bdc1ba39ec86e817fa2";

    private ApkUploadValidator() {
    }

    public static void validate(Path apkPath, String expectedVersionName) throws IOException {
        String expectedVersion = trimOrNull(expectedVersionName);
        if (expectedVersion == null) {
            throw new IllegalArgumentException("未配置目标 Android 版本号");
        }

        try (ApkFile apkFile = new ApkFile(apkPath.toFile())) {
            ApkMeta meta = apkFile.getApkMeta();
            if (meta == null) {
                throw new IllegalArgumentException("无法解析 APK 元数据");
            }

            String packageName = trimOrNull(meta.getPackageName());
            if (packageName == null) {
                throw new IllegalArgumentException("APK 缺少包名");
            }
            if (!EXPECTED_PACKAGE_NAME.equals(packageName)) {
                throw new IllegalArgumentException(
                        "APK 包名不匹配，期望 " + EXPECTED_PACKAGE_NAME + "，实际 " + packageName);
            }

            String versionName = trimOrNull(meta.getVersionName());
            if (versionName == null) {
                throw new IllegalArgumentException("APK 缺少 versionName");
            }
            if (!expectedVersion.equals(versionName)) {
                throw new IllegalArgumentException(
                        "APK versionName 不匹配，期望 " + expectedVersion + "，实际 " + versionName);
            }

            LinkedHashSet<String> fingerprints = new LinkedHashSet<>();
            collectFingerprints(apkFile.getApkSingers(), fingerprints);
            collectV2Fingerprints(apkFile.getApkV2Singers(), fingerprints);
            if (fingerprints.isEmpty()) {
                throw new IllegalArgumentException("APK 未检测到有效签名");
            }
            if (!fingerprints.contains(EXPECTED_CERT_SHA256)) {
                throw new IllegalArgumentException("APK 签名证书不受信任");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("无效的 APK 文件: " + e.getMessage(), e);
        }
    }

    private static void collectFingerprints(List<ApkSigner> signers, Set<String> out) {
        if (signers == null) {
            return;
        }
        for (ApkSigner signer : signers) {
            addCertificateMetas(signer.getCertificateMetas(), out);
        }
    }

    private static void collectV2Fingerprints(List<ApkV2Signer> signers, Set<String> out) {
        if (signers == null) {
            return;
        }
        for (ApkV2Signer signer : signers) {
            addCertificateMetas(signer.getCertificateMetas(), out);
        }
    }

    private static void addCertificateMetas(List<CertificateMeta> metas, Set<String> out) {
        if (metas == null) {
            return;
        }
        for (CertificateMeta meta : metas) {
            String fp = sha256Fingerprint(meta.getData());
            if (!fp.isEmpty()) {
                out.add(fp);
            }
        }
    }

    private static String sha256Fingerprint(byte[] certBytes) {
        if (certBytes == null || certBytes.length == 0) {
            return "";
        }
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(certBytes));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(cert.getEncoded());
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String trimOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
