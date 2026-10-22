package com.neko.music.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpRegionServiceTest {

    @Test
    void maxmindCityIsPreferredWhenAvailable() {
        IpLocation maxmind = new IpLocation("CN", "中国", "广东省", "广州市");
        IpLocation ip2region = new IpLocation("CN", "中国", "广东省", "深圳市");

        assertEquals("广州", IpRegionService.format(maxmind, IpLocation.empty(), ip2region));
    }

    @Test
    void qqwryCityFillsMissingMaxmindCity() {
        IpLocation qqwry = new IpLocation("CN", "中国", "江西省", "南昌市");

        assertEquals("南昌", IpRegionService.format(
                new IpLocation("CN", "中国", "", ""), qqwry, IpLocation.empty()));
    }

    @Test
    void ip2regionCityFillsMissingMaxmindCity() {
        IpLocation maxmind = new IpLocation("CN", "中国", "", "");
        IpLocation ip2region = new IpLocation("CN", "中国", "广东省", "深圳市");

        assertEquals("深圳", IpRegionService.format(maxmind, IpLocation.empty(), ip2region));
    }

    @Test
    void domesticFallsBackToProvince() {
        IpLocation maxmind = new IpLocation("CN", "中国", "广东省", "");
        IpLocation ip2region = new IpLocation("CN", "中国", "", "");

        assertEquals("广东", IpRegionService.format(maxmind, IpLocation.empty(), ip2region));
    }

    @Test
    void domesticWithoutProvinceOrCityFallsBackToCountry() {
        IpLocation maxmind = new IpLocation("CN", "中国", "", "");

        assertEquals("中国", IpRegionService.format(maxmind, IpLocation.empty(), IpLocation.empty()));
    }

    @Test
    void foreignOnlyReturnsCountry() {
        IpLocation maxmind = new IpLocation("US", "美国", "California", "Mountain View");
        IpLocation ip2region = new IpLocation("US", "United States", "California", "Mountain View");

        assertEquals("美国", IpRegionService.format(maxmind, IpLocation.empty(), ip2region));
    }

    @Test
    void foreignCountryCodeWithoutChineseNameIsMapped() {
        IpLocation ip2region = new IpLocation("US", "United States", "California", "Mountain View");

        assertEquals("美国", IpRegionService.format(
                IpLocation.empty(), IpLocation.empty(), ip2region));
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
