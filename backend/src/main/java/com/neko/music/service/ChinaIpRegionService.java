package com.neko.music.service;

import com.neko.music.util.AtomicFiles;
import org.lionsoul.ip2region.service.Config;
import org.lionsoul.ip2region.service.ConfigBuilder;
import org.lionsoul.ip2region.service.Ip2Region;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * 国内 IPv4 / IPv6 城市级归属地兜底。
 *
 * <p>ip2region 的 v4、v6 是两份独立数据库，必须按地址族分别加载；任一份缺失时只降级对应地址族，
 * 不影响另一份和评论发表。数据库优先从运行目录读取，缺失时尝试从 JAR 释放到磁盘；
 * 磁盘不可写时退回内存缓存模式。</p>
 */
final class ChinaIpRegionService implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(ChinaIpRegionService.class);

    private static final String V4_NAME = "ip2region_v4.xdb";
    private static final String V6_NAME = "ip2region_v6.xdb";

    private static final String[] V4_FILE_CANDIDATES = {
            "GeoIP/" + V4_NAME,
            V4_NAME,
            "/app/GeoIP/" + V4_NAME,
    };
    private static final String[] V6_FILE_CANDIDATES = {
            "GeoIP/" + V6_NAME,
            V6_NAME,
            "/app/GeoIP/" + V6_NAME,
    };
    private static final String[] V4_RESOURCES = {"/GeoIP/" + V4_NAME, "/" + V4_NAME};
    private static final String[] V6_RESOURCES = {"/GeoIP/" + V6_NAME, "/" + V6_NAME};

    private final Ip2Region searcher;

    private ChinaIpRegionService(Ip2Region searcher) {
        this.searcher = searcher;
    }

    static ChinaIpRegionService open() {
        Config v4 = loadConfig(true);
        Config v6 = loadConfig(false);
        if (v4 == null && v6 == null) {
            logger.warn("未找到 ip2region v4/v6 数据库，国内评论归属地省市将返回 unknown。"
                    + "请确认 JAR 内包含 /GeoIP/ip2region_v4.xdb 与 /GeoIP/ip2region_v6.xdb，"
                    + "或把文件放到 GeoIP/ 或 /app/GeoIP/");
            return null;
        }
        if (v4 == null) {
            logger.warn("未找到 ip2region IPv4 数据库，IPv4 国内归属地省市将返回 unknown");
        }
        if (v6 == null) {
            logger.warn("未找到 ip2region IPv6 数据库，IPv6 国内归属地省市将返回 unknown");
        }
        try {
            return new ChinaIpRegionService(Ip2Region.create(v4, v6));
        } catch (Exception e) {
            logger.warn("ip2region 国内归属地数据库加载失败: {}", e.getMessage());
            return null;
        }
    }

    IpLocation lookup(String ip) {
        try {
            return parse(searcher.search(ip));
        } catch (Exception e) {
            logger.debug("ip2region 解析失败: ip={}, {}", ip, e.getMessage());
            return IpLocation.empty();
        }
    }

    static IpLocation parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return IpLocation.empty();
        }
        String[] parts = raw.split("\\|", -1);
        String country = field(parts, 0);
        String province = field(parts, 1);
        String city = field(parts, 2);
        String iso = field(parts, 4);
        if (iso.isEmpty()) {
            if ("中国".equals(country)) {
                iso = "CN";
            } else if ("United States".equalsIgnoreCase(country)) {
                iso = "US";
            }
        }
        return new IpLocation(iso.toUpperCase(Locale.ROOT), country, province, city);
    }

    private static Config loadConfig(boolean v4) {
        String name = v4 ? V4_NAME : V6_NAME;
        String[] fileCandidates = v4 ? V4_FILE_CANDIDATES : V6_FILE_CANDIDATES;
        String[] resources = v4 ? V4_RESOURCES : V6_RESOURCES;

        for (String candidate : fileCandidates) {
            Path path = Paths.get(candidate);
            if (Files.isRegularFile(path)) {
                logger.info("国内 IP 归属地数据库已加载: {} ({})", path.toAbsolutePath(),
                        v4 ? "IPv4" : "IPv6");
                Config config = buildFileConfig(path.toFile(), v4);
                if (config != null) {
                    return config;
                }
            }
        }

        Path target = writableTarget(name);
        if (target != null) {
            for (String resource : resources) {
                try (InputStream in = ChinaIpRegionService.class.getResourceAsStream(resource)) {
                    if (in == null) {
                        continue;
                    }
                    AtomicFiles.writeAndReplace(in, target);
                    logger.info("已从 JAR 释放国内 IP 归属地数据库: {} ({})",
                            target.toAbsolutePath(), v4 ? "IPv4" : "IPv6");
                    Config config = buildFileConfig(target.toFile(), v4);
                    if (config != null) {
                        return config;
                    }
                } catch (Exception e) {
                    logger.warn("释放国内 IP 归属地数据库失败: {} - {}", resource, e.getMessage());
                }
            }
        }

        for (String resource : resources) {
            try (InputStream in = ChinaIpRegionService.class.getResourceAsStream(resource)) {
                if (in == null) {
                    continue;
                }
                ConfigBuilder builder = Config.custom()
                        .setCachePolicy(Config.BufferCache)
                        .setXdbInputStream(in);
                Config config = v4 ? builder.asV4() : builder.asV6();
                logger.info("国内 IP 归属地数据库已从 JAR 加载: {} ({})", resource, v4 ? "IPv4" : "IPv6");
                return config;
            } catch (Exception e) {
                logger.warn("JAR 内国内 IP 归属地数据库加载失败: {} - {}", resource, e.getMessage());
            }
        }
        return null;
    }

    private static Config buildFileConfig(File file, boolean v4) {
        try {
            ConfigBuilder builder = Config.custom().setXdbFile(file);
            return v4 ? builder.asV4() : builder.asV6();
        } catch (Exception e) {
            logger.warn("国内 IP 归属地数据库无法读取: {} - {}", file, e.getMessage());
            return null;
        }
    }

    private static Path writableTarget(String name) {
        Path local = Paths.get("GeoIP", name);
        if (ensureWritableDir(local.toAbsolutePath().getParent())) {
            return local;
        }
        Path container = Paths.get("/app", "GeoIP", name);
        if (ensureWritableDir(container.getParent())) {
            return container;
        }
        return null;
    }

    private static boolean ensureWritableDir(Path dir) {
        if (dir == null) {
            return false;
        }
        try {
            Files.createDirectories(dir);
            return Files.isWritable(dir);
        } catch (IOException e) {
            return false;
        }
    }

    private static String field(String[] parts, int index) {
        if (parts == null || index >= parts.length) {
            return "";
        }
        String value = parts[index] == null ? "" : parts[index].trim();
        return "0".equals(value) ? "" : value;
    }

    @Override
    public void close() throws InterruptedException {
        searcher.close();
    }
}
