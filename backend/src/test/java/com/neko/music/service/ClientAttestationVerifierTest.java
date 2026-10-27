package com.neko.music.service;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERSet;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
