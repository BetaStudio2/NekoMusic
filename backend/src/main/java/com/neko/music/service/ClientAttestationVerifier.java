package com.neko.music.service;

import com.neko.music.util.ClientIdentityProtocol;
import org.bouncycastle.asn1.ASN1Boolean;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Enumerated;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1Set;
import org.bouncycastle.asn1.ASN1TaggedObject;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Android Key Attestation 校验。
 *
 * <p>校验信任 Google 的工厂根与 RKP 根，并要求 KeyDescription 的 challenge、包名与 APK 签名
 * 摘要全部匹配。硬件证明通过时记录 TEE / StrongBox；厂商证明异常时降级为
 * {@code tee_unverified} / {@code software}，由调用方决定是否限制敏感功能。</p>
 */
public final class ClientAttestationVerifier {

    private static final String KEY_DESCRIPTION_OID = "1.3.6.1.4.1.11129.2.1.17";
    private static final String GOOGLE_FACTORY_ROOT_PUBLIC_KEY =
            "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAr7bHgiuxpwHsK7Qui8xU"
                    + "FmOr75gvMsd/dTEDDJdSSxtf6An7xyqpRR90PL2abxM1dEqlXnf2tqw1Ne4Xwl5j"
                    + "lRfdnJLmN0pTy/4lj4/7tv0Sk3iiKkypnEUtR6WfMgH0QZfKHM1+di+y9TFRtv6y"
                    + "//0rb+T+W8a9nsNL/ggjnar86461qO0rOs2cXjp3kOG1FEJ5MVmFmBGtnrKpa73X"
                    + "pXyTqRxB/M0n1n/W9nGqC4FSYa04T6N5RIZGBN2z2MT5IKGbFlbC8UrW0DxW7AYI"
                    + "mQQcHtGl/m00QLVWutHQoVJYnFPlXTcHYvASLu+RhhsbDmxMgJJ0mcDpvsC4PjvB"
                    + "+TxywElgS70vE0XmLD+OJtvsBslHZvPBKCOdT0MS+tgSOIfga+z1Z1g7+DVagf7q"
                    + "uvmag8jfPioyKvxnK/EgsTUVi2ghzq8wm27ud/mIM7AY2qEORR8Go3TVB4HzWQgp"
                    + "Zrt3i5MIlCaY504LzSRiigHCzAPlHws+W0rB5N+er5/2pJKnfBSDiCiFAVtCLOZ7"
                    + "gLiMm0jhO2B6tUXHI/+MRPjy02i59lINMRRev56GKtcd9qO/0kUJWdZTdA2XoS82"
                    + "ixPvZtXQpUpuL12ab+9EaDK8Z4RHJYYfCT3Q5vNAXaiWQ+8PTWm2QgBR/bkwSWc+"
                    + "NpUFgNPN9PvQi8WEg5UmAGMCAwEAAQ==";
    private static final String GOOGLE_RKP_ROOT_PUBLIC_KEY =
            "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEI9ojcU7fPlsFCjxy6IRqzgeOoK0b+YsV"
                    + "9FPQywiyw8EQRTkJ9u3qwfnI4DGoSLlBqClTXJfgfCcZvs60FikNMHnu4fkRzObf"
                    + "gDkU2KNXezT9/RQ+XvNslxPHrHCowhGr";

    private static final List<TrustedRootSpec> GOOGLE_ROOT_SPECS = List.of(
            new TrustedRootSpec("RSA", GOOGLE_FACTORY_ROOT_PUBLIC_KEY, true),
            new TrustedRootSpec("EC", GOOGLE_RKP_ROOT_PUBLIC_KEY, false));

    private static final int ATTESTATION_CHALLENGE_INDEX = 4;
    private static final int SW_ENFORCED_INDEX = 6;
    private static final int TEE_ENFORCED_INDEX = 7;
    private static final int SECURITY_LEVEL_TEE = 1;
    private static final int SECURITY_LEVEL_STRONGBOX = 2;

    private static final int TAG_ALGORITHM = 2;
    private static final int TAG_KEY_SIZE = 3;
    private static final int TAG_EC_CURVE = 10;
    private static final int TAG_ROOT_OF_TRUST = 704;
    private static final int TAG_ATTESTATION_APPLICATION_ID = 709;
    private static final int ALGORITHM_EC = 3;
    private static final int EC_CURVE_P256 = 1;

    private static volatile List<TrustedRoot> pinnedRoots;

    private ClientAttestationVerifier() {
    }

    public record Result(String securityLevel, String applicationId, String signingDigest,
                         boolean hardwareVerified, String failureReason) {
        /** 注册响应和数据库使用的可信等级；TEE 链存在但启动状态异常时保留降级信息。 */
        public String assuranceLevel() {
            if (hardwareVerified) return securityLevel;
            if (SECURITY_LEVEL_TEE_NAME.equals(securityLevel)
                    || SECURITY_LEVEL_STRONGBOX_NAME.equals(securityLevel)) {
                return "tee_unverified";
            }
            return "software";
        }
    }

    private static final String SECURITY_LEVEL_TEE_NAME = "tee";
    private static final String SECURITY_LEVEL_STRONGBOX_NAME = "strongbox";

    private record TrustedRootSpec(String algorithm, String publicKeyBase64, boolean factoryKey) {
    }

    private record TrustedRoot(PublicKey publicKey, boolean factoryKey) {
    }

    /**
     * @param chainBase64Der 证书链（叶证书在前，根证书在后），每项是 DER 的 base64url/base64
     * @param expectedSpki   客户端提交的 EC SPKI（base64url）
     * @param expectedChallenge 新生成密钥时可核对证书内的 challenge；密钥跨多次注册尝试复用时传
     *                          {@code null}，当前持有者由注册请求的私钥签名证明
     */
    public static Result verify(List<String> chainBase64Der,
                                String expectedSpki,
                                String expectedChallenge,
                                String expectedPackageName,
                                String expectedSigningDigest) {
        if (chainBase64Der == null || chainBase64Der.size() < 2 || chainBase64Der.size() > 6) {
            throw new IllegalArgumentException("attestation chain length invalid");
        }
        try {
            List<X509Certificate> chain = parseChain(chainBase64Der);
            for (int i = 0; i < chain.size() - 1; i++) {
                chain.get(i).verify(chain.get(i + 1).getPublicKey());
            }
            X509Certificate last = chain.get(chain.size() - 1);
            TrustedRoot trustedRoot = findTrustedRoot(last);
            if (trustedRoot == null) {
                throw new IllegalArgumentException("attestation root is not pinned: "
                        + describeCertificate(last));
            }
            // 旧工厂证书允许过期（Google 官方要求继续信任），RKP 证书必须检查有效期。
            if (!trustedRoot.factoryKey()) {
                for (X509Certificate certificate : chain) {
                    certificate.checkValidity();
                }
            }
            X509Certificate leaf = chain.get(0);
            if (!java.util.Arrays.equals(leaf.getPublicKey().getEncoded(),
                    ClientIdentityProtocol.parseEcPublicKey(expectedSpki).getEncoded())) {
                throw new IllegalArgumentException("attestation public key mismatch");
            }
            byte[] extension = leaf.getExtensionValue(KEY_DESCRIPTION_OID);
            if (extension == null) {
                throw new IllegalArgumentException("missing key attestation extension");
            }
            byte[] inner = ASN1OctetString.getInstance(extension).getOctets();
            ASN1Sequence keyDescription = ASN1Sequence.getInstance(inner);
            int securityLevel = integer(keyDescription.getObjectAt(1));
            if (securityLevel != SECURITY_LEVEL_TEE && securityLevel != SECURITY_LEVEL_STRONGBOX) {
                throw new IllegalArgumentException("hardware attestation required");
            }
            if (expectedChallenge != null && !expectedChallenge.isBlank()) {
                byte[] challenge = ASN1OctetString.getInstance(
                        keyDescription.getObjectAt(ATTESTATION_CHALLENGE_INDEX)).getOctets();
                if (!java.util.Arrays.equals(challenge,
                        expectedChallenge.getBytes(StandardCharsets.UTF_8))) {
                    throw new IllegalArgumentException("attestation challenge mismatch");
                }
            }

            ASN1Sequence software = ASN1Sequence.getInstance(
                    keyDescription.getObjectAt(SW_ENFORCED_INDEX));
            ASN1Sequence tee = ASN1Sequence.getInstance(
                    keyDescription.getObjectAt(TEE_ENFORCED_INDEX));
            List<ASN1Sequence> enforced = List.of(software, tee);

            AttestationAppId appId = null;
            RootState root = null;
            for (ASN1Sequence list : enforced) {
                for (int i = 0; i < list.size(); i++) {
                    ASN1TaggedObject tagged = ASN1TaggedObject.getInstance(list.getObjectAt(i));
                    ASN1Encodable value = tagged.getExplicitBaseObject();
                    if (tagged.getTagNo() == TAG_ATTESTATION_APPLICATION_ID) {
                        appId = parseApplicationId(value);
                    } else if (tagged.getTagNo() == TAG_ROOT_OF_TRUST) {
                        root = parseRootOfTrust(ASN1Sequence.getInstance(value));
                    }
                }
            }
            checkKeyParameters(enforced);
            if (appId == null || !appId.packageNames().contains(expectedPackageName)) {
                throw new IllegalArgumentException("attestation package mismatch");
            }
            String digest = expectedSigningDigest == null
                    ? "" : expectedSigningDigest.toLowerCase(Locale.ROOT);
            boolean digestMatched = appId.signatureDigests().stream()
                    .anyMatch(candidate -> candidate.equalsIgnoreCase(digest));
            if (!digestMatched) {
                throw new IllegalArgumentException("attestation signing digest mismatch");
            }
            String level = securityLevel == SECURITY_LEVEL_STRONGBOX
                    ? SECURITY_LEVEL_STRONGBOX_NAME : SECURITY_LEVEL_TEE_NAME;
            if (root == null || !root.deviceLocked()
                    || (root.verifiedBootState() != 0 && root.verifiedBootState() != 1)) {
                throw new UntrustedBootStateException(level, root == null
                        ? "device boot state is not trusted: rootOfTrust missing"
                        : "device boot state is not trusted: deviceLocked=" + root.deviceLocked()
                        + ", verifiedBootState=" + root.verifiedBootState());
            }
            return new Result(level, expectedPackageName, digest, true, null);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("attestation verification failed", e);
        }
    }

    /**
     * 注册场景使用：证明链不可用时返回降级等级，而不是中断注册。
     *
     * <p>调用方仍必须先校验挑战和实例私钥签名；本方法只处理 Key Attestation 的可靠性。</p>
     */
    public static Result assess(List<String> chainBase64Der,
                                String expectedSpki,
                                String expectedChallenge,
                                String expectedPackageName,
                                String expectedSigningDigest) {
        try {
            return verify(chainBase64Der, expectedSpki, expectedChallenge,
                    expectedPackageName, expectedSigningDigest);
        } catch (UntrustedBootStateException e) {
            return new Result(e.securityLevel, "", "", false, e.getMessage());
        } catch (RuntimeException e) {
            return new Result("software", "", "", false,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private static List<X509Certificate> parseChain(List<String> values) throws Exception {
        List<X509Certificate> chain = new ArrayList<>();
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        for (String value : values) {
            if (value == null || value.length() > 32_768) {
                throw new IllegalArgumentException("attestation certificate too large");
            }
            byte[] der = decodeCertificate(value);
            chain.add((X509Certificate) factory.generateCertificate(new ByteArrayInputStream(der)));
        }
        return chain;
    }

    private static byte[] decodeCertificate(String value) {
        try {
            return Base64.getUrlDecoder().decode(value.trim());
        } catch (IllegalArgumentException ignored) {
            return Base64.getDecoder().decode(value.trim());
        }
    }

    private static TrustedRoot findTrustedRoot(X509Certificate last) throws Exception {
        List<TrustedRoot> roots = googleRoots();
        for (TrustedRoot root : roots) {
            try {
                last.verify(root.publicKey());
                return root;
            } catch (Exception ignored) {
                // 换下一把受信根继续验证。
            }
        }
        return null;
    }

    /** 供测试核对根证书轮换；生产校验仍走 {@link #verify}。 */
    static boolean isTrustedAttestationRoot(X509Certificate certificate) throws Exception {
        return findTrustedRoot(certificate) != null;
    }

    private static List<TrustedRoot> googleRoots() throws Exception {
        List<TrustedRoot> cached = pinnedRoots;
        if (cached != null) {
            return cached;
        }
        synchronized (ClientAttestationVerifier.class) {
            cached = pinnedRoots;
            if (cached != null) {
                return cached;
            }
            List<TrustedRoot> roots = new ArrayList<>();
            for (TrustedRootSpec spec : GOOGLE_ROOT_SPECS) {
                byte[] der = Base64.getDecoder().decode(spec.publicKeyBase64());
                PublicKey key = KeyFactory.getInstance(spec.algorithm())
                        .generatePublic(new X509EncodedKeySpec(der));
                roots.add(new TrustedRoot(key, spec.factoryKey()));
            }
            cached = List.copyOf(roots);
            pinnedRoots = cached;
            return cached;
        }
    }

    private static String describeCertificate(X509Certificate certificate) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
            return certificate.getSubjectX500Principal().getName()
                    + " sha256=" + java.util.HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return certificate.getSubjectX500Principal().getName();
        }
    }

    private static void checkKeyParameters(List<ASN1Sequence> enforced) {
        Integer algorithm = null;
        Integer keySize = null;
        Integer curve = null;
        for (ASN1Sequence list : enforced) {
            for (int i = 0; i < list.size(); i++) {
                ASN1TaggedObject tagged = ASN1TaggedObject.getInstance(list.getObjectAt(i));
                int tag = tagged.getTagNo();
                if (tag == TAG_ALGORITHM) {
                    algorithm = integer(tagged.getExplicitBaseObject());
                } else if (tag == TAG_KEY_SIZE) {
                    keySize = integer(tagged.getExplicitBaseObject());
                } else if (tag == TAG_EC_CURVE) {
                    curve = integer(tagged.getExplicitBaseObject());
                }
            }
        }
        if ((algorithm != null && algorithm != ALGORITHM_EC)
                || (keySize != null && keySize != 256)
                || (curve != null && curve != EC_CURVE_P256)) {
            throw new IllegalArgumentException("attestation key must be EC P-256");
        }
    }

    record AttestationAppId(List<String> packageNames, List<String> signatureDigests) {
    }

    /**
     * Android Key Attestation 的 tag 709 实际编码是 OCTET STRING，里面再包一层
     * AttestationApplicationId SEQUENCE；部分实现也可能直接给 SEQUENCE，两种都要接受。
     */
    static AttestationAppId parseApplicationId(ASN1Encodable value) {
        ASN1Encodable encoded = value;
        if (value instanceof ASN1OctetString octets) {
            encoded = ASN1Sequence.getInstance(octets.getOctets());
        }
        ASN1Sequence sequence = ASN1Sequence.getInstance(encoded);
        ASN1Set packages = ASN1Set.getInstance(sequence.getObjectAt(0));
        ASN1Set digests = ASN1Set.getInstance(sequence.getObjectAt(1));
        List<String> packageNames = new ArrayList<>();
        for (int i = 0; i < packages.size(); i++) {
            ASN1Sequence packageInfo = ASN1Sequence.getInstance(packages.getObjectAt(i));
            packageNames.add(new String(
                    ASN1OctetString.getInstance(packageInfo.getObjectAt(0)).getOctets(),
                    StandardCharsets.UTF_8));
        }
        List<String> signatureDigests = new ArrayList<>();
        for (int i = 0; i < digests.size(); i++) {
            signatureDigests.add(java.util.HexFormat.of().formatHex(
                    ASN1OctetString.getInstance(digests.getObjectAt(i)).getOctets()));
        }
        return new AttestationAppId(packageNames, signatureDigests);
    }

    private record RootState(boolean deviceLocked, int verifiedBootState) {
    }

    private static final class UntrustedBootStateException extends IllegalArgumentException {
        private final String securityLevel;

        private UntrustedBootStateException(String securityLevel, String message) {
            super(message);
            this.securityLevel = securityLevel;
        }
    }

    private static RootState parseRootOfTrust(ASN1Sequence sequence) {
        if (sequence.size() < 3) {
            throw new IllegalArgumentException("invalid root of trust");
        }
        boolean locked = ASN1Boolean.getInstance(sequence.getObjectAt(1)).isTrue();
        int state = integer(sequence.getObjectAt(2));
        return new RootState(locked, state);
    }

    private static int integer(ASN1Encodable value) {
        if (value instanceof ASN1Integer integer) {
            return integer.getValue().intValueExact();
        }
        if (value instanceof ASN1Enumerated enumerated) {
            return enumerated.getValue().intValueExact();
        }
        throw new IllegalArgumentException("ASN.1 integer expected");
    }
}
