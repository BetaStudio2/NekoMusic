package com.neko.music.service;

/** 单个 IP 的归属地字段；空字符串表示该字段缺失。 */
record IpLocation(String countryCode, String countryName, String province, String city) {

    static IpLocation empty() {
        return new IpLocation("", "", "", "");
    }

    boolean hasCountry() {
        return (countryCode != null && !countryCode.isBlank())
                || (countryName != null && !countryName.isBlank());
    }
}
