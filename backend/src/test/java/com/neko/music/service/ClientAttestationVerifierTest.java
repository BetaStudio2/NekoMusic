package com.neko.music.service;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERSet;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientAttestationVerifierTest {

    @Test
    void parsesApplicationIdWrappedInOctetString() throws Exception {
        String digest = "53a64cdb1491a2b28a3d309e41e92addbabcc691b8fb5bdc1ba39ec86e817fa2";
        ASN1Sequence sequence = applicationId("com.neko.music", digest);
        ASN1Encodable wrapped = new DEROctetString(sequence.getEncoded());

        ClientAttestationVerifier.AttestationAppId parsed =
                ClientAttestationVerifier.parseApplicationId(wrapped);

        assertEquals(List.of("com.neko.music"), parsed.packageNames());
        assertEquals(List.of(digest), parsed.signatureDigests());
    }

    @Test
    void stillParsesDirectApplicationIdSequence() {
        String digest = "53a64cdb1491a2b28a3d309e41e92addbabcc691b8fb5bdc1ba39ec86e817fa2";
        ClientAttestationVerifier.AttestationAppId parsed =
                ClientAttestationVerifier.parseApplicationId(
                        applicationId("com.neko.music", digest));

        assertEquals(List.of("com.neko.music"), parsed.packageNames());
        assertEquals(List.of(digest), parsed.signatureDigests());
    }

    @Test
    void invalidAttestationFallsBackToSoftwareInsteadOfRejectingRegistration() {
        ClientAttestationVerifier.Result result = ClientAttestationVerifier.assess(
                List.of("not-a-certificate", "still-not-a-certificate"),
                "invalid-spki", "challenge", "com.neko.music",
                "53a64cdb1491a2b28a3d309e41e92addbabcc691b8fb5bdc1ba39ec86e817fa2");

        assertFalse(result.hardwareVerified());
        assertEquals("software", result.assuranceLevel());
    }

    @Test
    void failedRootOfTrustIsReportedAsTeeUnverified() {
        ClientAttestationVerifier.Result result = new ClientAttestationVerifier.Result(
                "tee", "", "", false, "device boot state is not trusted");

        assertEquals("tee_unverified", result.assuranceLevel());
    }

    @Test
    void trustsBothFactoryAndRkpGoogleRoots() throws Exception {
        X509Certificate rkpRoot = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(Base64.getMimeDecoder().decode(
                        """
                        MIICIjCCAaigAwIBAgIRAISp0Cl7DrWK5/8OgN52BgUwCgYIKoZIzj0EAwMwUjEc
                        MBoGA1UEAwwTS2V5IEF0dGVzdGF0aW9uIENBMTEQMA4GA1UECwwHQW5kcm9pZDET
                        MBEGA1UECgwKR29vZ2xlIExMQzELMAkGA1UEBhMCVVMwHhcNMjUwNzE3MjIzMjE4
                        WhcNMzUwNzE1MjIzMjE4WjBSMRwwGgYDVQQDDBNLZXkgQXR0ZXN0YXRpb24gQ0Ex
                        MRAwDgYDVQQLDAdBbmRyb2lkMRMwEQYDVQQKDApHb29nbGUgTExDMQswCQYDVQQG
                        EwJVUzB2MBAGByqGSM49AgEGBSuBBAAiA2IABCPaI3FO3z5bBQo8cuiEas4HjqCt
                        G/mLFfRT0MsIssPBEEU5Cfbt6sH5yOAxqEi5QagpU1yX4HwnGb7OtBYpDTB57uH5
                        Eczm34A5FNijV3s0/f0UPl7zbJcTx6xwqMIRq6NCMEAwDwYDVR0TAQH/BAUwAwEB
                        /zAOBgNVHQ8BAf8EBAMCAQYwHQYDVR0OBBYEFFIyuyz7RkOb3NaBqQ5lZuA0QepA
                        MAoGCCqGSM49BAMDA2gAMGUCMETfjPO/HwqReR2CS7p0ZWoD/LHs6hDi422opifH
                        EUaYLxwGlT9SLdjkVpz0UUOR5wIxAIoGyxGKRHVTpqpGRFiJtQEOOTp/+s1GcxeY
                        uR2zh/80lQyu9vAFCj6E4AXc+osmRg==
                        """)));

        assertTrue(ClientAttestationVerifier.isTrustedAttestationRoot(rkpRoot));
    }

    private static ASN1Sequence applicationId(String packageName, String digest) {
        ASN1EncodableVector packages = new ASN1EncodableVector();
        packages.add(new DERSequence(new DEROctetString(
                packageName.getBytes(StandardCharsets.UTF_8))));
        ASN1EncodableVector digests = new ASN1EncodableVector();
        digests.add(new DEROctetString(HexFormat.of().parseHex(digest)));
        ASN1EncodableVector fields = new ASN1EncodableVector();
        fields.add(new DERSet(packages));
        fields.add(new DERSet(digests));
        return new DERSequence(fields);
    }
}
