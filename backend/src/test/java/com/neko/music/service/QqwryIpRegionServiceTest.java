package com.neko.music.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QqwryIpRegionServiceTest {

    @Test
    void parsesProvinceCityAndDistrict() {
        IpLocation location = QqwryIpRegionService.parse("中国–江西–南昌–南昌县");

        assertEquals("CN", location.countryCode());
        assertEquals("中国", location.countryName());
        assertEquals("江西", location.province());
        assertEquals("南昌", location.city());
    }

    @Test
    void parsesProvinceOnly() {
        IpLocation location = QqwryIpRegionService.parse("中国–江西");

        assertEquals("江西", location.province());
        assertEquals("", location.city());
    }

    @Test
    void keepsForeignCountryNameIntact() {
        IpLocation location = QqwryIpRegionService.parse("United States");

        assertEquals("United States", location.countryName());
        assertTrue(location.province().isBlank());
    }

    @Test
    void emptyRecordIsEmpty() {
        IpLocation location = QqwryIpRegionService.parse("");

        assertTrue(location.countryCode().isBlank());
        assertTrue(location.province().isBlank());
        assertTrue(location.city().isBlank());
    }
}
