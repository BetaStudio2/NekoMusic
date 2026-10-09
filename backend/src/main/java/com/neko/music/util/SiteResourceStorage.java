package com.neko.music.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.zip.CRC32;

/**
 * 前端站点资源：直接从 classpath 读取（生产环境即后端 JAR 内的 {@code site/}）。
 *
 * <p>这里**不再**把站点解压到运行目录：前端不落地，就不会在磁盘上留下一份可能与 JAR 内不一致
 * 的副本，也不给「绕过本服务、用别的 Web 服务器直接托管这些文件」留出旁路——前端只有后端这
 * 一个来源。</p>
 *
 * <p>资源在首次使用时整包读进内存（当前构建产物只有几 MB），请求路径上只做一次 Map 查表。
 * 若将来站点涨到几十 MB 以上，应改为「只建索引、按需流式读取」。</p>
 */
public final class SiteResourceStorage {

    private static final Logger logger = LoggerFactory.getLogger(SiteResourceStorage.class);

    /** classpath 内的站点根目录名。 */
    private static final String RESOURCE_ROOT = "site";

    private static volatile Map<String, Entry> index;

    private SiteResourceStorage() {
    }

    /** 单个前端资源；{@code path} 形如 {@code /assets/app-xxxx.js}。 */
    public record Entry(String path, byte[] content, String etag, long lastModified) {
    }

    /** 站点资源索引（懒加载一次）。键是站点根下的绝对路径，值不可变。 */
    public static Map<String, Entry> index() throws IOException {
        Map<String, Entry> cached = index;
        if (cached != null) {
            return cached;
        }
        synchronized (SiteResourceStorage.class) {
            if (index == null) {
                index = load();
            }
            return index;
        }
    }

    private static Map<String, Entry> load() throws IOException {
        ClassLoader classLoader = SiteResourceStorage.class.getClassLoader();
        // 某些 classloader 只为目录返回带末尾斜杠的 URL，两种写法都试一次。
        URL root = classLoader.getResource(RESOURCE_ROOT + "/");
        if (root == null) {
            root = classLoader.getResource(RESOURCE_ROOT);
        }
        if (root == null) {
            logger.error("classpath 内未找到前端站点资源（site/），页面请求将一律 404");
            return Map.of();
        }

        Map<String, Entry> result = new LinkedHashMap<>();
        switch (root.getProtocol()) {
            case "file" -> collectFromDirectory(root, result);
            case "jar" -> collectFromJar(root, result);
            default -> throw new IOException("不支持的站点资源协议: " + root.getProtocol());
        }
        logger.info("前端站点资源已载入内存: {} 个文件", result.size());
        return Map.copyOf(result);
    }

    /** 开发/未打包时（target/classes/site）直接遍历目录。 */
    private static void collectFromDirectory(URL root, Map<String, Entry> target) throws IOException {
        Path directory;
        try {
            directory = Path.of(root.toURI());
        } catch (URISyntaxException e) {
            throw new IOException("站点资源路径无效: " + root, e);
        }
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                String path = toResourcePath(directory.relativize(file).toString());
                byte[] content = Files.readAllBytes(file);
                long lastModified = Files.getLastModifiedTime(file).toMillis();
                target.put(path, new Entry(path, content, etagFor(content), lastModified));
            }
        }
    }

    /** 打包后从 JAR 条目读取；不缓存 JarFile 句柄，读完即关。 */
    private static void collectFromJar(URL root, Map<String, Entry> target) throws IOException {
        JarURLConnection connection = (JarURLConnection) root.openConnection();
        connection.setUseCaches(false);
        String prefix = RESOURCE_ROOT + "/";
        try (JarFile jar = connection.getJarFile()) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().startsWith(prefix)) {
                    continue;
                }
                String path = toResourcePath(entry.getName().substring(prefix.length()));
                byte[] content;
                try (InputStream input = jar.getInputStream(entry)) {
                    content = input.readAllBytes();
                }
                target.put(path, new Entry(path, content, etagFor(content), entry.getTime()));
            }
        }
    }

    /** 把相对路径归一成 {@code /a/b} 形式的资源键。 */
    private static String toResourcePath(String relative) {
        String normalized = relative.replace(File.separatorChar, '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return "/" + normalized;
    }

    /** 内容摘要式强 ETag：内容不变则跨重启一致，改动后必然变化。 */
    private static String etagFor(byte[] content) {
        CRC32 crc = new CRC32();
        crc.update(content);
        return "\"" + Long.toHexString(crc.getValue()) + "-" + Integer.toHexString(content.length) + "\"";
    }
}
