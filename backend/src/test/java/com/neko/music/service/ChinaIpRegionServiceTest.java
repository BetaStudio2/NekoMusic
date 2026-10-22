package com.neko.music.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChinaIpRegionServiceTest {

    @Test
    void parsesChinaIpv4Record() {
        IpLocation location = ChinaIpRegionService.parse("中国|广东省|深圳市|电信|CN");

        assertEquals("CN", location.countryCode());
        assertEquals("广东省", location.province());
        assertEquals("深圳市", location.city());
    }

    @Test
    void parsesChinaRecordWithoutProvinceOrCity() {
        IpLocation location = ChinaIpRegionService.parse("中国|0|0|移动|CN");

        assertEquals("CN", location.countryCode());
        assertEquals("", location.province());
        assertEquals("", location.city());
    }

    @Test
    void parsesIpv6Record() {
        IpLocation location = ChinaIpRegionService.parse("中国|广东省|深圳市|电信|CN");

        assertEquals("深圳市", location.city());
    }

    @Test
    void parsesForeignRecordWithOriginalCity() {
        IpLocation location = ChinaIpRegionService.parse("United States|California|0|Google LLC|US");

        assertEquals("US", location.countryCode());
        assertEquals("United States", location.countryName());
        assertEquals("California", location.province());
        assertEquals("", location.city());
    }

    @Test
    void parsesForeignRecordCityWithoutFiltering() {
        IpLocation location = ChinaIpRegionService.parse("United States|California|Mountain View|Google LLC|US");

        assertEquals("California", location.province());
        assertEquals("Mountain View", location.city());
    }

    @Test
    void emptyRecordIsEmpty() {
        IpLocation location = ChinaIpRegionService.parse("0|0|0|0|0");

        assertTrue(location.countryCode().isBlank());
        assertTrue(location.countryName().isBlank());
        assertTrue(location.city().isBlank());
    }
}
