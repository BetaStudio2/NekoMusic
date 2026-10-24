package com.neko.music.service;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.record.Country;
import com.maxmind.geoip2.record.Subdivision;
import com.neko.music.util.AtomicFiles;
import com.neko.music.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 评论 IP 归属地：省级运营商 ASN 命中时直接按运营商网络定省；
 * 未命中时再按本地 MaxMind 判国家 / 省市，IPv4 由 qqwry 补省、市。
 * 全程读取本地数据库，不请求任何第三方接口。
 *
 * <p>MaxMind 数据库按以下顺序自动发现（IPv4 与 IPv6 同一份 City/Country 库即可覆盖；
 * ASN 库使用同样的文件 / 类路径发现方式）：
 * <ol>
 *   <li>运行目录下的 {@code GeoIP/GeoLite2-City.mmdb}（JAR 内置库释放出来的位置）</li>
 *   <li>{@code /app/GeoIP/}、{@code /usr/share/GeoIP/}、{@code /usr/local/share/GeoIP/}、{@code /var/lib/GeoIP/}</li>
 *   <li>类路径 {@code /GeoLite2-City.mmdb}（{@code src/main/resources/} 根目录，随 JAR 分发）</li>
 * </ol>
 *
 * <p>数据库随 JAR 分发（源码在 {@code src/main/resources/GeoLite2-City.mmdb}）。若运行目录里还没有库文件，
 * 服务启动时会自动把 JAR 内置的库释放到 {@code GeoIP/GeoLite2-City.mmdb} 再加载，无需人工放置文件；
 * 磁盘不可写时退化为直接从 JAR 流式读取。
 *
 * <p>国外只返回国家名；国内普通省份只显示省级行政区，北京 / 上海 / 天津 / 重庆等直辖市显示市名；
 * 国内省市都解析不到时回退到国家名，完全无法识别时返回「其他」；内网 / 回环地址返回「本地」。
 * 任一数据库缺失时只降级对应能力，不影响评论发表。
 */
public class IpRegionService {

    private static final Logger logger = LoggerFactory.getLogger(IpRegionService.class);

    private static final String FALLBACK = "其他";
    private static final String LEGACY_UNKNOWN = "未知";
    private static final String LOCAL = "本地";
    private static final String ZH = "zh-CN";
    private static final int MEMORY_CACHE_MAX = 8192;

    /** 从 JAR 释放数据库时的落盘位置（相对运行目录；容器内即 /app/GeoIP/...），与 FILE_CANDIDATES 首项一致 */
    private static final Path PRIMARY_DATABASE = Paths.get("GeoIP", "GeoLite2-City.mmdb");
    private static final Path CONTAINER_DATABASE = Paths.get("/app", "GeoIP", "GeoLite2-City.mmdb");

    /** 外部文件候选路径，按优先级排列。 */
    private static final String[] FILE_CANDIDATES = {
            "GeoIP/GeoLite2-City.mmdb",
            "GeoIP/GeoLite2-Country.mmdb",
            "GeoLite2-City.mmdb",
            "GeoLite2-Country.mmdb",
            "data/GeoLite2-City.mmdb",
            "/app/GeoIP/GeoLite2-City.mmdb",
            "/app/GeoIP/GeoLite2-Country.mmdb",
            "/app/GeoLite2-City.mmdb",
            "/usr/share/GeoIP/GeoLite2-City.mmdb",
            "/usr/share/GeoIP/GeoLite2-Country.mmdb",
            "/usr/local/share/GeoIP/GeoLite2-City.mmdb",
            "/var/lib/GeoIP/GeoLite2-City.mmdb",
    };

    /** 类路径候选资源：数据库放在 {@code src/main/resources/} 根目录（推荐），也兼容 GeoIP/ 子目录。 */
    private static final String[] CLASSPATH_CANDIDATES = {
            "/GeoLite2-City.mmdb",
            "/GeoLite2-Country.mmdb",
            "/GeoIP/GeoLite2-City.mmdb",
            "/GeoIP/GeoLite2-Country.mmdb",
    };

    /**
     * 精简版 GeoLite2（只保留 ISO 国家码、删除多语言名称）时的兜底中文名。
     * 标准 GeoLite2 自带 zh-CN 名称，用不到这张表。
     */
    private static final java.util.Map<String, String> COUNTRY_ZH = java.util.Map.ofEntries(
            java.util.Map.entry("CN", "中国"),
            java.util.Map.entry("HK", "中国香港"),
            java.util.Map.entry("TW", "中国台湾"),
            java.util.Map.entry("MO", "中国澳门"),
            java.util.Map.entry("US", "美国"),
            java.util.Map.entry("JP", "日本"),
            java.util.Map.entry("KR", "韩国"),
            java.util.Map.entry("KP", "朝鲜"),
            java.util.Map.entry("SG", "新加坡"),
            java.util.Map.entry("MY", "马来西亚"),
            java.util.Map.entry("TH", "泰国"),
            java.util.Map.entry("VN", "越南"),
            java.util.Map.entry("PH", "菲律宾"),
            java.util.Map.entry("ID", "印度尼西亚"),
            java.util.Map.entry("IN", "印度"),
            java.util.Map.entry("PK", "巴基斯坦"),
            java.util.Map.entry("BD", "孟加拉国"),
            java.util.Map.entry("LK", "斯里兰卡"),
            java.util.Map.entry("NP", "尼泊尔"),
            java.util.Map.entry("MM", "缅甸"),
            java.util.Map.entry("KH", "柬埔寨"),
            java.util.Map.entry("LA", "老挝"),
            java.util.Map.entry("MN", "蒙古"),
            java.util.Map.entry("KZ", "哈萨克斯坦"),
            java.util.Map.entry("AU", "澳大利亚"),
            java.util.Map.entry("NZ", "新西兰"),
            java.util.Map.entry("GB", "英国"),
            java.util.Map.entry("IE", "爱尔兰"),
            java.util.Map.entry("FR", "法国"),
            java.util.Map.entry("DE", "德国"),
            java.util.Map.entry("IT", "意大利"),
            java.util.Map.entry("ES", "西班牙"),
            java.util.Map.entry("PT", "葡萄牙"),
            java.util.Map.entry("NL", "荷兰"),
            java.util.Map.entry("BE", "比利时"),
            java.util.Map.entry("CH", "瑞士"),
            java.util.Map.entry("AT", "奥地利"),
            java.util.Map.entry("SE", "瑞典"),
            java.util.Map.entry("NO", "挪威"),
            java.util.Map.entry("DK", "丹麦"),
            java.util.Map.entry("FI", "芬兰"),
            java.util.Map.entry("PL", "波兰"),
            java.util.Map.entry("CZ", "捷克"),
            java.util.Map.entry("GR", "希腊"),
            java.util.Map.entry("TR", "土耳其"),
            java.util.Map.entry("RU", "俄罗斯"),
            java.util.Map.entry("UA", "乌克兰"),
            java.util.Map.entry("CA", "加拿大"),
            java.util.Map.entry("MX", "墨西哥"),
            java.util.Map.entry("BR", "巴西"),
            java.util.Map.entry("AR", "阿根廷"),
            java.util.Map.entry("CL", "智利"),
            java.util.Map.entry("ZA", "南非"),
            java.util.Map.entry("EG", "埃及"),
            java.util.Map.entry("AE", "阿联酋"),
            java.util.Map.entry("SA", "沙特阿拉伯"),
            java.util.Map.entry("IL", "以色列"),
            java.util.Map.entry("IR", "伊朗"),
            java.util.Map.entry("IQ", "伊拉克")
    );

    private final DatabaseReader reader;
    private final AsnIpRegionService asnService;
    private final QqwryIpRegionService qqwryService;
    private final ConcurrentMap<String, String> cache = new ConcurrentHashMap<>();

    public IpRegionService() {
        this.reader = openDatabase();
        this.asnService = AsnIpRegionService.open();
        this.qqwryService = QqwryIpRegionService.open();
    }

    /** 任一归属地数据库是否可用。 */
    public boolean isAvailable() {
        return reader != null || asnService != null || qqwryService != null;
    }

    /** 解析归属地；国内显示省级行政区，直辖市显示市名，完全无法识别时返回「其他」。 */
    public String resolve(String rawIp) {
        String ip = normalize(rawIp);
        if (ip.isEmpty()) {
            return FALLBACK;
        }
        if (isLocalAddress(ip)) {
            return LOCAL;
        }
        String cached = cache.get(ip);
        if (cached != null) {
            return cached;
        }

        InetAddress address;
        try {
            // 已校验为 IP 字面量，不会触发 DNS 查询
            address = InetAddress.getByName(ip);
        } catch (Exception e) {
            return FALLBACK;
        }

        IpLocation maxmind = lookupMaxMind(reader, address);
        IpLocation asn = asnService == null ? IpLocation.empty() : asnService.lookup(address);
        IpLocation qqwry = IpLocation.empty();
        if (asn.province().isBlank() && shouldUseChina(maxmind) && qqwryService != null) {
            qqwry = qqwryService.lookup(ip);
        }
        String region = format(maxmind, asn, qqwry);
        if (cache.size() >= MEMORY_CACHE_MAX) {
            cache.clear();
        }
        cache.put(ip, region);
        return region;
    }

    private static IpLocation lookupMaxMind(DatabaseReader db, InetAddress address) {
        if (db == null) {
            return IpLocation.empty();
        }
        boolean cityDatabase = isCityDatabase(db);
        if (cityDatabase) {
            IpLocation city = lookupCity(db, address);
            if (city.hasCountry() || !city.province().isBlank() || !city.city().isBlank()) {
                return city;
            }
        }
        IpLocation country = lookupCountry(db, address);
        if (country.hasCountry()) {
            return country;
        }
        if (!cityDatabase) {
            IpLocation city = lookupCity(db, address);
            if (city.hasCountry() || !city.province().isBlank() || !city.city().isBlank()) {
                return city;
            }
        }
        return IpLocation.empty();
    }

    private static boolean isCityDatabase(DatabaseReader db) {
        try {
            String type = db.metadata().databaseType();
            return type != null && type.toLowerCase().contains("city");
        } catch (Exception e) {
            return true;
        }
    }

    private static IpLocation lookupCity(DatabaseReader db, InetAddress address) {
        try {
            return db.tryCity(address)
                    .map(response -> new IpLocation(
                            countryCode(response.country()),
                            countryName(response.country()),
                            subdivisionName(response.subdivisions()),
                            localized(response.city().names())))
                    .orElse(IpLocation.empty());
        } catch (Exception e) {
            logger.debug("City 库解析失败: {}", e.getMessage());
            return IpLocation.empty();
        }
    }

    private static IpLocation lookupCountry(DatabaseReader db, InetAddress address) {
        try {
            return db.tryCountry(address)
                    .map(response -> new IpLocation(
                            countryCode(response.country()),
                            countryName(response.country()), "", ""))
                    .orElse(IpLocation.empty());
        } catch (Exception e) {
            logger.debug("Country 库解析失败: {}", e.getMessage());
            return IpLocation.empty();
        }
    }

    private static String subdivisionName(List<Subdivision> subdivisions) {
        if (subdivisions == null || subdivisions.isEmpty()) {
            return "";
        }
        // 取最具体的一级（省 / 州）
        return localized(subdivisions.get(subdivisions.size() - 1).names());
    }

    /** 国家代码：统一转成大写 ISO 两字母码。 */
    private static String countryCode(Country country) {
        if (country == null || country.isoCode() == null) {
            return "";
        }
        return country.isoCode().toUpperCase(Locale.ROOT);
    }

    private static String countryName(Country country) {
        if (country == null) {
            return "";
        }
        String name = localized(country.names());
        if (!name.isEmpty()) {
            return name;
        }
        String iso = country.isoCode();
        if (iso == null) {
            return "";
        }
        return COUNTRY_ZH.getOrDefault(iso.toUpperCase(), iso.toUpperCase());
    }

    /** 优先中文名，退化到英文，再退化到任意可用名称。 */
    private static String localized(Map<String, String> names) {
        if (names == null || names.isEmpty()) {
            return "";
        }
        String value = names.get(ZH);
        if (value == null || value.isBlank()) {
            value = names.get("zh");
        }
        if (value == null || value.isBlank()) {
            value = names.get("en");
        }
        if (value == null || value.isBlank()) {
            value = names.values().stream().filter(v -> v != null && !v.isBlank()).findFirst().orElse("");
        }
        return value == null ? "" : value.trim();
    }

    /**
     * 国内普通省份只返回省级行政区，直辖市返回市名；港澳台带「中国」前缀，国外只返回国家名。
     * 省级运营商 ASN 命中时优先于地理库的国家 / 省份判定；ASN 未命中时回退到 MaxMind / qqwry。
     */
    static String format(IpLocation maxmind, IpLocation asn, IpLocation qqwry) {
        IpLocation mm = maxmind == null ? IpLocation.empty() : maxmind;
        IpLocation as = asn == null ? IpLocation.empty() : asn;
        IpLocation qw = qqwry == null ? IpLocation.empty() : qqwry;

        String countryCode = firstNonBlank(as.countryCode(), mm.countryCode(), qw.countryCode())
                .toUpperCase(Locale.ROOT);
        String countryName = firstNonBlank(as.countryName(), mm.countryName(), qw.countryName());
        if (countryCode.isEmpty() && ("中国".equals(countryName) || "China".equalsIgnoreCase(countryName))) {
            countryCode = "CN";
        }

        String hmt = hmtName(countryCode, countryName);
        if (!hmt.isEmpty()) {
            return hmt;
        }
        if (!"CN".equals(countryCode)) {
            String mapped = countryCode.isBlank() ? "" : COUNTRY_ZH.getOrDefault(countryCode, "");
            if (!mapped.isBlank()) {
                return mapped;
            }
            return countryName.isBlank() ? FALLBACK : countryName;
        }
        String province = shortenRegionName(firstNonBlank(as.province(), mm.province(), qw.province()));
        if (!province.isEmpty()) {
            return province;
        }
        String cityName = shortenRegionName(firstNonBlank(mm.city(), qw.city()));
        if (isMunicipality(cityName)) {
            return cityName;
        }
        return countryName.isBlank() ? "中国" : countryName;
    }

    /** 直辖市属于省级行政区，省级数据缺失时才允许用市名补位。 */
    private static boolean isMunicipality(String name) {
        return "北京".equals(name) || "上海".equals(name) || "天津".equals(name) || "重庆".equals(name)
                || "Beijing".equalsIgnoreCase(name) || "Shanghai".equalsIgnoreCase(name)
                || "Tianjin".equalsIgnoreCase(name) || "Chongqing".equalsIgnoreCase(name);
    }

    /**
     * 港澳台统一显示为「中国香港」「中国澳门」「中国台湾」：
     * GeoLite2 的本地化名称只给裸名（香港 / 澳门 / 台湾），这里补上「中国」前缀。
     */
    private static String hmtName(String countryCode, String countryName) {
        String code = countryCode == null ? "" : countryCode.trim();
        String name = countryName == null ? "" : countryName.trim();
        if ("HK".equalsIgnoreCase(code) || name.contains("香港") || name.equalsIgnoreCase("Hong Kong")) {
            return "中国香港";
        }
        if ("MO".equalsIgnoreCase(code) || name.contains("澳门") || name.contains("澳門")
                || name.equalsIgnoreCase("Macao") || name.equalsIgnoreCase("Macau")) {
            return "中国澳门";
        }
        if ("TW".equalsIgnoreCase(code) || name.contains("台湾") || name.contains("台灣")
                || name.equalsIgnoreCase("Taiwan")) {
            return "中国台湾";
        }
        return "";
    }

    private static boolean shouldUseChina(IpLocation maxmind) {
        if (maxmind == null || !maxmind.hasCountry()) {
            return true;
        }
        return "CN".equalsIgnoreCase(maxmind.countryCode())
                || "中国".equals(maxmind.countryName())
                || "China".equalsIgnoreCase(maxmind.countryName());
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    /** 历史评论可能保存过 unknown / 未知，接口输出时统一归一，避免客户端继续看到旧占位。 */
    public static String displayStored(String region) {
        if (region == null || region.isBlank()) {
            return FALLBACK;
        }
        String value = region.trim();
        if ("unknown".equalsIgnoreCase(value) || LEGACY_UNKNOWN.equals(value)) {
            return FALLBACK;
        }
        return value;
    }

    /**
     * 去掉行政区划后缀，避免出现「上海市上海」这类重复拼接：
     * 上海市→上海、贵州省→贵州、广西壮族自治区→广西、延边朝鲜族自治州→延边、香港特别行政区→香港。
     */
    private static String shortenRegionName(String raw) {
        if (raw == null) {
            return "";
        }
        String name = raw.trim();
        if (name.isEmpty()) {
            return "";
        }
        name = name.replaceFirst("(特别行政区|自治区|自治州|省|市|地区|盟)$", "");
        name = name.replaceFirst(
                "(维吾尔|壮族|回族|蒙古族|朝鲜族|苗族|侗族|藏族|彝族|白族|傣族|哈尼族|布依族|土家族"
                        + "|傈僳族|拉祜族|佤族|纳西族|景颇族|羌族|水族|仡佬族|畲族|黎族|瑶族|满族|土族|达斡尔族"
                        + "|仫佬族|毛南族|京族|裕固族|锡伯族|鄂温克族|鄂伦春族|赫哲族|门巴族|珞巴族|基诺族"
                        + "|德昂族|阿昌族|普米族|怒族|独龙族|布朗族|撒拉族|保安族|东乡族)+$", "");
        return name.trim();
    }

    /** 去掉端口、IPv6 方括号与 IPv4-mapped 前缀（统一复用 {@link ClientIpResolver} 的实现）。 */
    static String normalize(String raw) {
        return ClientIpResolver.normalize(raw);
    }

    static boolean isLocalAddress(String ip) {
        if (ip.isEmpty()) {
            return false;
        }
        if ("localhost".equalsIgnoreCase(ip) || "::1".equals(ip)) {
            return true;
        }
        if (ip.contains(":")) {
            String lower = ip.toLowerCase();
            return lower.startsWith("fc") || lower.startsWith("fd") || lower.startsWith("fe80");
        }
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        try {
            int a = Integer.parseInt(parts[0]);
            int b = Integer.parseInt(parts[1]);
            if (a == 10 || a == 127) {
                return true;
            }
            if (a == 192 && b == 168) {
                return true;
            }
            if (a == 172 && b >= 16 && b <= 31) {
                return true;
            }
            return a == 169 && b == 254;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** 从请求还原真实客户端 IP（统一走 {@link ClientIpResolver}，避免信任可伪造的转发头）。 */
    public static String clientIp(HttpServletRequest request) {
        return ClientIpResolver.clientIp(request);
    }

    /** 依次尝试外部文件与类路径资源；JAR 内置库会在启动时释放到磁盘。全部失败返回 null。 */
    private static DatabaseReader openDatabase() {
        DatabaseReader db = openFromFiles();
        if (db != null) {
            return db;
        }
        db = openFromClasspath();
        if (db != null) {
            return db;
        }
        logDatabaseMissing();
        return null;
    }

    private static DatabaseReader openFromFiles() {
        for (String candidate : FILE_CANDIDATES) {
            Path path = Paths.get(candidate);
            if (Files.isRegularFile(path)) {
                DatabaseReader db = buildReader(path.toFile());
                if (db != null) {
                    logger.info("IP 归属地数据库已加载: {} ({}), IPv4/IPv6 均使用该库",
                            path.toAbsolutePath(), databaseType(db));
                    return db;
                }
            }
        }
        return null;
    }

    /** 类路径候选：把 JAR 内置库释放到磁盘（后端目录里就有一份），磁盘不可写时直接从 JAR 读取。 */
    private static DatabaseReader openFromClasspath() {
        Path target = writableDatabaseTarget();
        IOException releaseFailure = null;
        if (target != null) {
            for (String resource : CLASSPATH_CANDIDATES) {
                try (InputStream in = IpRegionService.class.getResourceAsStream(resource)) {
                    if (in == null) {
                        continue;
                    }
                    AtomicFiles.writeAndReplace(in, target);
                    DatabaseReader db = buildReader(target.toFile());
                    if (db != null) {
                        logger.info("已从 JAR 释放 IP 归属地数据库: {} ({})",
                                target.toAbsolutePath(), databaseType(db));
                        return db;
                    }
                } catch (IOException e) {
                    releaseFailure = e;
                    logger.warn("释放 JAR 内 IP 归属地数据库失败: {} - {}", resource, e.getMessage());
                } catch (Exception e) {
                    logger.warn("加载 JAR 内 IP 归属地数据库失败: {} - {}", resource, e.getMessage());
                }
            }
        }
        for (String resource : CLASSPATH_CANDIDATES) {
            try (InputStream in = IpRegionService.class.getResourceAsStream(resource)) {
                if (in == null) {
                    continue;
                }
                DatabaseReader db = new DatabaseReader.Builder(in).build();
                logger.info("IP 归属地数据库已从 JAR 内加载: {} ({})", resource, databaseType(db));
                return db;
            } catch (Exception e) {
                logger.warn("JAR 内 IP 归属地数据库加载失败: {} - {}", resource, e.getMessage());
            }
        }
        if (releaseFailure != null) {
            logger.warn("JAR 内 IP 归属地数据库未能释放到磁盘: {}", releaseFailure.getMessage());
        }
        return null;
    }

    /** 优先运行目录下的 GeoIP/，容器里退到 /app/GeoIP/；都不可写返回 null。 */
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
            logger.warn("IP 归属地数据库无法读取: {} - {}", file, e.getMessage());
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

    private static void logDatabaseMissing() {
        logger.warn("未找到 MaxMind GeoLite2 数据库，评论归属地将无法解析国外国家。"
                        + "请确认 JAR 内包含 /GeoLite2-City.mmdb（源码位于 src/main/resources/），"
                        + "或把数据库放到 {} 或 /app/GeoIP/GeoLite2-City.mmdb。",
                PRIMARY_DATABASE.toAbsolutePath());
    }
}
