package com.neko.music.service;

import com.github.jarod.qqwry.IPZone;
import com.github.jarod.qqwry.QQWry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * 国内 IPv4 省市补充：纯真数据库对运营商地址的覆盖通常比 MaxMind / ip2region 更完整。
 * 只处理 IPv4；IPv6 交给 MaxMind 与 ip2region。数据库缺失时静默降级。
 */
final class QqwryIpRegionService {

    private static final Logger logger = LoggerFactory.getLogger(QqwryIpRegionService.class);

    private static final String NAME = "qqwry.dat";
    private static final String[] FILE_CANDIDATES = {
            "GeoIP/" + NAME,
            NAME,
            "/app/GeoIP/" + NAME,
            "/usr/share/GeoIP/" + NAME,
            "/var/lib/GeoIP/" + NAME,
    };
    private static final String[] RESOURCES = {"/GeoIP/" + NAME, "/" + NAME};
    private static final Pattern REGION_SEPARATOR = Pattern.compile("\\s*[–—-]\\s*");

    private final QQWry database;

    private QqwryIpRegionService(QQWry database) {
        this.database = database;
    }

    static QqwryIpRegionService open() {
        for (String candidate : FILE_CANDIDATES) {
            Path path = Paths.get(candidate);
            if (!Files.isRegularFile(path)) {
                continue;
            }
            try {
                QQWry database = new QQWry(Files.readAllBytes(path));
                logger.info("国内 IPv4 补充数据库已加载: {} (qqwry {})",
                        path.toAbsolutePath(), database.getDatabaseVersion());
                return new QqwryIpRegionService(database);
            } catch (Exception e) {
                logger.warn("国内 IPv4 补充数据库无法读取: {} - {}", path, e.getMessage());
            }
        }

        for (String resource : RESOURCES) {
            try (InputStream in = QqwryIpRegionService.class.getResourceAsStream(resource)) {
                if (in == null) {
                    continue;
                }
                QQWry database = new QQWry(in.readAllBytes());
                logger.info("国内 IPv4 补充数据库已从 JAR 加载: {} (qqwry {})",
                        resource, database.getDatabaseVersion());
                return new QqwryIpRegionService(database);
            } catch (IOException | RuntimeException e) {
                logger.warn("JAR 内国内 IPv4 补充数据库加载失败: {} - {}", resource, e.getMessage());
            }
        }
        logger.warn("未找到 qqwry.dat，国内 IPv4 将继续使用 MaxMind / ip2region");
        return null;
    }

    IpLocation lookup(String ip) {
        if (ip == null || ip.isBlank() || ip.indexOf(':') >= 0) {
            return IpLocation.empty();
        }
        try {
            IPZone zone = database.findIP(ip);
            return parse(zone == null ? "" : zone.getMainInfo());
        } catch (Exception e) {
            logger.debug("qqwry 解析失败: ip={}, {}", ip, e.getMessage());
            return IpLocation.empty();
        }
    }

    static IpLocation parse(String mainInfo) {
        String value = mainInfo == null ? "" : mainInfo.trim();
        if (value.isEmpty()) {
            return IpLocation.empty();
        }
        String[] parts = REGION_SEPARATOR.split(value);
        if (parts.length == 1 && value.startsWith("中国")) {
            parts = value.split("\\s+");
        }
        String country = field(parts, 0);
        if (country.isEmpty()) {
            return IpLocation.empty();
        }
        boolean china = country.startsWith("中国");
        if (china) {
            return new IpLocation("CN", "中国", field(parts, 1), field(parts, 2));
        }
        return new IpLocation("", country, field(parts, 1), field(parts, 2));
    }

    private static String field(String[] parts, int index) {
        if (parts == null || index >= parts.length || parts[index] == null) {
            return "";
        }
        return parts[index].trim();
    }
}
