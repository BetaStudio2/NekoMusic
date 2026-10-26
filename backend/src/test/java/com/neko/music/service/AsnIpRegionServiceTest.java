package com.neko.music.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsnIpRegionServiceTest {

    @Test
    void regionalAsnMapsToProvince() {
        IpLocation location = AsnIpRegionService.parse(56045L, Map.of(56045L, "江西"));

        assertEquals("CN", location.countryCode());
        assertEquals("中国", location.countryName());
        assertEquals("江西", location.province());
        assertEquals("", location.city());
    }

    @Test
    void nationalAsnKeepsFallback() {
        IpLocation location = AsnIpRegionService.parse(9808L, Map.of(56045L, "江西"));

        assertTrue(location.countryCode().isBlank());
        assertTrue(location.province().isBlank());
    }

    @Test
    void detectsCommercialIspDatabase() {
        assertTrue(AsnIpRegionService.isIspDatabaseType("GeoIP2-ISP"));
        assertFalse(AsnIpRegionService.isIspDatabaseType("GeoLite2-ASN"));
        assertFalse(AsnIpRegionService.isIspDatabaseType(null));
    }

    @Test
    void loadsTabSeparatedMappingAndIgnoresComments() throws Exception {
        InputStream input = new ByteArrayInputStream((
                "# comment\n"
                        + "56045\t江西\n"
                        + "56040\t广东\n"
                        + "bad\t江西\n"
                        + "56046\n").getBytes(StandardCharsets.UTF_8));

        Map<Long, String> regions = AsnIpRegionService.loadRegions(input);

        assertEquals("江西", regions.get(56045L));
        assertEquals("广东", regions.get(56040L));
        assertFalse(regions.containsKey(56046L));
    }

    @Test
    void bundledMappingContainsProvincialCarriers() throws Exception {
        try (InputStream input = AsnIpRegionServiceTest.class.getResourceAsStream("/GeoIP/asn_regions.tsv")) {
            assertTrue(input != null, "缺少 /GeoIP/asn_regions.tsv");
            Map<Long, String> regions = AsnIpRegionService.loadRegions(input);

            assertEquals("江西", regions.get(56045L));
            assertEquals("广东", regions.get(56040L));
            assertEquals("江苏", regions.get(56046L));
            assertFalse(regions.containsKey(9808L));
            assertFalse(regions.containsKey(4134L));
            assertFalse(regions.containsKey(4837L));
        }
    }
}
