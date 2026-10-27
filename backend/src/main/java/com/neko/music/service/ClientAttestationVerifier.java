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
 * <p>校验只信任 Google 硬件证明根公钥，并要求 KeyDescription 的 challenge、包名与 APK 签名摘要
 * 全部匹配。安全级别必须是 TEE 或 StrongBox；软件级证明不能用于官方 Android 客户端。</p>
 */
public final class ClientAttestationVerifier {

    private static final String KEY_DESCRIPTION_OID = "1.3.6.1.4.1.11129.2.1.17";
    private static final String GOOGLE_ROOT_PUBLIC_KEY =
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

    private static volatile PublicKey pinnedRoot;

    private ClientAttestationVerifier() {
    }

    public record Result(String securityLevel, String applicationId, String signingDigest) {
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
            PublicKey googleRoot = googleRoot();
            if (!java.util.Arrays.equals(chain.get(chain.size() - 1).getPublicKey().getEncoded(),
                    googleRoot.getEncoded())) {
                throw new IllegalArgumentException("attestation root is not pinned");
            }
            X509Certificate leaf = chain.get(0);
            leaf.checkValidity();
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
            if (root == null || !root.deviceLocked()
                    || (root.verifiedBootState() != 0 && root.verifiedBootState() != 1)) {
                throw new IllegalArgumentException("device boot state is not trusted");
            }
            return new Result(securityLevel == SECURITY_LEVEL_STRONGBOX ? "strongbox" : "tee",
                    expectedPackageName, digest);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("attestation verification failed", e);
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

    private static PublicKey googleRoot() throws Exception {
        PublicKey cached = pinnedRoot;
        if (cached != null) {
            return cached;
        }
        byte[] der = Base64.getDecoder().decode(GOOGLE_ROOT_PUBLIC_KEY);
        cached = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        pinnedRoot = cached;
        return cached;
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
