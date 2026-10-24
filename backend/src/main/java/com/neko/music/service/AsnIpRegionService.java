package com.neko.music.service;

import com.maxmind.geoip2.DatabaseReader;
import com.neko.music.util.AtomicFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 按 ASN 判断国内省级运营商归属。
 *
 * <p>ASN 只能说明网络属于哪家运营商 / 哪个省级网络，不能定位到城市；因此这里只返回
 * 省 / 直辖市，城市仍由 MaxMind / qqwry 兜底。全国性 ASN（如 9808、4134、4837）
 * 不在映射表内，会自然回退到地理库。数据库全部从本地读取，不请求第三方接口。</p>
 */
final class AsnIpRegionService {

    private static final Logger logger = LoggerFactory.getLogger(AsnIpRegionService.class);

    private static final String DATABASE_NAME = "GeoLite2-ASN.mmdb";
    private static final String REGION_RESOURCE = "/GeoIP/asn_regions.tsv";
    private static final Path PRIMARY_DATABASE = Paths.get("GeoIP", DATABASE_NAME);
    private static final Path CONTAINER_DATABASE = Paths.get("/app", "GeoIP", DATABASE_NAME);

    private static final String[] FILE_CANDIDATES = {
            "GeoIP/" + DATABASE_NAME,
            DATABASE_NAME,
            "/app/GeoIP/" + DATABASE_NAME,
            "/usr/share/GeoIP/" + DATABASE_NAME,
            "/usr/local/share/GeoIP/" + DATABASE_NAME,
            "/var/lib/GeoIP/" + DATABASE_NAME,
    };
    private static final String[] CLASSPATH_CANDIDATES = {
            "/" + DATABASE_NAME,
            "/GeoIP/" + DATABASE_NAME,
    };

    private final DatabaseReader reader;
    private final Map<Long, String> regions;

    private AsnIpRegionService(DatabaseReader reader, Map<Long, String> regions) {
        this.reader = reader;
        this.regions = regions;
    }

    static AsnIpRegionService open() {
        DatabaseReader reader = openDatabase();
        if (reader == null) {
            logger.warn("未找到 GeoLite2-ASN 数据库，评论归属地将直接回退到 MaxMind / qqwry。"
                    + "请确认 JAR 内包含 /GeoLite2-ASN.mmdb，或把文件放到 GeoIP/ 或 /app/GeoIP/");
            return null;
        }
        Map<Long, String> regions = loadRegions();
        if (regions.isEmpty()) {
            logger.warn("ASN 省级映射为空，ASN 解析不会覆盖地理库结果");
        } else {
            logger.info("ASN 省级映射已加载: {} 条", regions.size());
        }
        return new AsnIpRegionService(reader, regions);
    }

    /** 返回 ASN 对应省份；全国性 / 未登记 ASN 返回空，交由地理库兜底。 */
    IpLocation lookup(InetAddress address) {
        if (reader == null || address == null || regions.isEmpty()) {
            return IpLocation.empty();
        }
        try {
            return reader.tryAsn(address)
                    .map(asn -> parse(asn.autonomousSystemNumber(), regions))
                    .orElse(IpLocation.empty());
        } catch (Exception e) {
            logger.debug("ASN 解析失败: ip={}, {}", address.getHostAddress(), e.getMessage());
            return IpLocation.empty();
        }
    }

    static IpLocation parse(Long asn, Map<Long, String> regions) {
        if (asn == null || regions == null || regions.isEmpty()) {
            return IpLocation.empty();
        }
        String province = regions.get(asn);
        if (province == null || province.isBlank()) {
            return IpLocation.empty();
        }
        return new IpLocation("CN", "中国", province, "");
    }

    private static Map<Long, String> loadRegions() {
        try (InputStream in = AsnIpRegionService.class.getResourceAsStream(REGION_RESOURCE)) {
            if (in == null) {
                logger.warn("未找到 ASN 省级映射资源: {}", REGION_RESOURCE);
                return Map.of();
            }
            return loadRegions(in);
        } catch (IOException e) {
            logger.warn("读取 ASN 省级映射失败: {} - {}", REGION_RESOURCE, e.getMessage());
            return Map.of();
        }
    }

    static Map<Long, String> loadRegions(InputStream in) throws IOException {
        Map<Long, String> result = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String value = line.trim();
                if (value.isEmpty() || value.startsWith("#")) {
                    continue;
                }
                String[] parts = value.split("\\t", -1);
                if (parts.length < 2) {
                    continue;
                }
                try {
                    long asn = Long.parseLong(parts[0].trim());
                    String province = parts[1].trim();
                    if (asn > 0 && !province.isEmpty()) {
                        result.put(asn, province);
                    }
                } catch (NumberFormatException e) {
                    logger.debug("忽略非法 ASN 映射行: {}", value);
                }
            }
        }
        return Map.copyOf(result);
    }

    private static DatabaseReader openDatabase() {
        DatabaseReader db = openFromFiles();
        if (db != null) {
            return db;
        }
        db = openFromClasspath();
        if (db != null) {
            return db;
        }
        return null;
    }

    private static DatabaseReader openFromFiles() {
        for (String candidate : FILE_CANDIDATES) {
            Path path = Paths.get(candidate);
            if (!Files.isRegularFile(path)) {
                continue;
            }
            DatabaseReader db = buildReader(path.toFile());
            if (db != null) {
                logger.info("ASN 数据库已加载: {} ({})", path.toAbsolutePath(), databaseType(db));
                return db;
            }
        }
        return null;
    }

    private static DatabaseReader openFromClasspath() {
        Path target = writableDatabaseTarget();
        if (target != null) {
            for (String resource : CLASSPATH_CANDIDATES) {
                try (InputStream in = AsnIpRegionService.class.getResourceAsStream(resource)) {
                    if (in == null) {
                        continue;
                    }
                    AtomicFiles.writeAndReplace(in, target);
                    DatabaseReader db = buildReader(target.toFile());
                    if (db != null) {
                        logger.info("已从 JAR 释放 ASN 数据库: {} ({})",
                                target.toAbsolutePath(), databaseType(db));
                        return db;
                    }
                } catch (Exception e) {
                    logger.warn("释放 JAR 内 ASN 数据库失败: {} - {}", resource, e.getMessage());
                }
            }
        }
        for (String resource : CLASSPATH_CANDIDATES) {
            try (InputStream in = AsnIpRegionService.class.getResourceAsStream(resource)) {
                if (in == null) {
                    continue;
                }
                DatabaseReader db = new DatabaseReader.Builder(in).build();
                logger.info("ASN 数据库已从 JAR 加载: {} ({})", resource, databaseType(db));
                return db;
            } catch (Exception e) {
                logger.warn("JAR 内 ASN 数据库加载失败: {} - {}", resource, e.getMessage());
            }
        }
        return null;
    }

    private static Path writableDatabaseTarget() {
        if (ensureWritableDir(PRIMARY_DATABASE.toAbsolutePath().getParent())) {
            return PRIMARY_DATABASE;
        }
        if (ensureWritableDir(CONTAINER_DATABASE.getParent())) {
            return CONTAINER_DATABASE;
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

    private static DatabaseReader buildReader(File file) {
        try {
            return new DatabaseReader.Builder(file).build();
        } catch (IOException e) {
            logger.warn("ASN 数据库无法读取: {} - {}", file, e.getMessage());
            return null;
        }
    }

    private static String databaseType(DatabaseReader db) {
        try {
            return db.metadata().databaseType();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
