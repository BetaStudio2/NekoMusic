package com.neko.music.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpRegionServiceTest {

    @Test
    void provinceIsPreferredOverCity() {
        IpLocation maxmind = new IpLocation("CN", "中国", "广东省", "广州市");
        IpLocation fallback = new IpLocation("CN", "中国", "广东省", "深圳市");

        assertEquals("广东", IpRegionService.format(maxmind, IpLocation.empty(), fallback));
    }

    @Test
    void qqwryFillsMissingProvince() {
        IpLocation qqwry = new IpLocation("CN", "中国", "江西省", "南昌市");

        assertEquals("江西", IpRegionService.format(
                new IpLocation("CN", "中国", "", ""), IpLocation.empty(), qqwry));
    }

    @Test
    void fallbackFillsMissingProvince() {
        IpLocation maxmind = new IpLocation("CN", "中国", "", "");
        IpLocation fallback = new IpLocation("CN", "中国", "广东省", "深圳市");

        assertEquals("广东", IpRegionService.format(maxmind, IpLocation.empty(), fallback));
    }

    @Test
    void asnProvinceOverridesGeoFallback() {
        IpLocation maxmind = new IpLocation("CN", "中国", "广东省", "广州市");
        IpLocation asn = new IpLocation("CN", "中国", "江西省", "");
        IpLocation qqwry = new IpLocation("CN", "中国", "广东省", "深圳市");

        assertEquals("江西", IpRegionService.format(maxmind, asn, qqwry));
    }

    @Test
    void asnProvinceOverridesForeignGeo() {
        IpLocation maxmind = new IpLocation("US", "美国", "California", "Mountain View");
        IpLocation asn = new IpLocation("CN", "中国", "江西省", "");

        assertEquals("江西", IpRegionService.format(maxmind, asn, IpLocation.empty()));
    }

    @Test
    void domesticReturnsProvince() {
        IpLocation maxmind = new IpLocation("CN", "中国", "广东省", "");
        IpLocation fallback = new IpLocation("CN", "中国", "", "");

        assertEquals("广东", IpRegionService.format(maxmind, IpLocation.empty(), fallback));
    }

    @Test
    void municipalityUsesItsProvinceLevelName() {
        IpLocation maxmind = new IpLocation("CN", "中国", "上海市", "上海市");

        assertEquals("上海", IpRegionService.format(maxmind, IpLocation.empty(), IpLocation.empty()));
    }

    @Test
    void municipalityFallsBackToCityWhenProvinceIsMissing() {
        IpLocation fallback = new IpLocation("CN", "中国", "", "北京市");

        assertEquals("北京", IpRegionService.format(IpLocation.empty(), IpLocation.empty(), fallback));
    }

    @Test
    void ordinaryCityWithoutProvinceDoesNotLeakCityName() {
        IpLocation fallback = new IpLocation("CN", "中国", "", "南昌市");

        assertEquals("中国", IpRegionService.format(IpLocation.empty(), IpLocation.empty(), fallback));
    }

    @Test
    void domesticWithoutProvinceOrCityFallsBackToCountry() {
        IpLocation maxmind = new IpLocation("CN", "中国", "", "");

        assertEquals("中国", IpRegionService.format(maxmind, IpLocation.empty(), IpLocation.empty()));
    }

    @Test
    void foreignOnlyReturnsCountry() {
        IpLocation maxmind = new IpLocation("US", "美国", "California", "Mountain View");
        IpLocation fallback = new IpLocation("US", "United States", "California", "Mountain View");

        assertEquals("美国", IpRegionService.format(maxmind, IpLocation.empty(), fallback));
    }

    @Test
    void foreignCountryCodeWithoutChineseNameIsMapped() {
        IpLocation fallback = new IpLocation("US", "United States", "California", "Mountain View");

        assertEquals("美国", IpRegionService.format(
                IpLocation.empty(), IpLocation.empty(), fallback));
    }

    @Test
    void hmtKeepsChinaPrefix() {
        assertEquals("中国香港", IpRegionService.format(
                new IpLocation("HK", "香港", "", ""), IpLocation.empty(), IpLocation.empty()));
        assertEquals("中国澳门", IpRegionService.format(
                new IpLocation("MO", "Macao", "", ""), IpLocation.empty(), IpLocation.empty()));
        assertEquals("中国台湾", IpRegionService.format(
                new IpLocation("TW", "台湾", "", ""), IpLocation.empty(), IpLocation.empty()));
    }

    @Test
    void completelyEmptyReturnsOther() {
        assertEquals("其他", IpRegionService.format(
                IpLocation.empty(), IpLocation.empty(), IpLocation.empty()));
    }

    @Test
    void legacyUnknownIsNormalizedForDisplay() {
        assertEquals("其他", IpRegionService.displayStored("unknown"));
        assertEquals("其他", IpRegionService.displayStored("未知"));
        assertEquals("其他", IpRegionService.displayStored(""));
        assertEquals("上海", IpRegionService.displayStored("上海"));
    }

    @Test
    void localIpv6IsDetected() {
        assertTrue(IpRegionService.isLocalAddress("fc00::1"));
        assertTrue(IpRegionService.isLocalAddress("fe80::1"));
    }
}
