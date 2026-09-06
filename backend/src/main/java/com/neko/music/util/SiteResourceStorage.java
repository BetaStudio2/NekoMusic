package com.neko.music.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 前端站点资源目录（固定为 {@code ${user.dir}/site}）。
 *
 * <p>生产构建会把 frontend/dist 输出到后端 classpath 的 {@code site/}，
 * 启动时再解压到运行目录，便于由外部 Web 服务器直接托管这些文件。</p>
 */
public final class SiteResourceStorage {
    private static final Logger logger = LoggerFactory.getLogger(SiteResourceStorage.class);
    private static final String RESOURCE_ROOT = "site";
    private static final String SITE_DIR_NAME = "site";

    private SiteResourceStorage() {
    }

    public static Path storageDir() {
        return Path.of(System.getProperty("user.dir"))
                .resolve(SITE_DIR_NAME)
                .toAbsolutePath()
                .normalize();
    }

    /** 仅在运行目录尚不存在时创建目录并释放 JAR 内嵌的前端资源。 */
    public static void ensureStorageDir() throws IOException {
        Path targetRoot = storageDir();
        if (Files.exists(targetRoot)) {
            if (!Files.isDirectory(targetRoot)) {
                throw new IOException("前端站点路径不是目录: " + targetRoot);
            }
            // 空目录可能是旧版本启动时创建的，只有包含入口文件才视为已初始化。
            if (Files.isRegularFile(targetRoot.resolve("index.html"))) {
                logger.debug("前端站点目录已初始化，跳过资源释放: {}", targetRoot);
                return;
            }
        }
        Files.createDirectories(targetRoot);

        ClassLoader classLoader = SiteResourceStorage.class.getClassLoader();
        URL resourceRoot = classLoader.getResource(RESOURCE_ROOT + "/");
        if (resourceRoot == null) {
            // 某些 classloader 只为目录返回不带末尾斜杠的 URL。
            resourceRoot = classLoader.getResource(RESOURCE_ROOT);
        }
        if (resourceRoot == null) {
            logger.debug("JAR 内未包含前端站点资源，已创建空目录: {}", targetRoot);
            return;
        }

        int copied;
        if ("file".equals(resourceRoot.getProtocol())) {
            try {
                copied = copyDirectory(Path.of(resourceRoot.toURI()), targetRoot);
            } catch (URISyntaxException e) {
                throw new IOException("站点资源路径无效: " + resourceRoot, e);
            }
        } else if ("jar".equals(resourceRoot.getProtocol())) {
            JarURLConnection connection = (JarURLConnection) resourceRoot.openConnection();
            connection.setUseCaches(false);
            copied = copyJar(connection.getJarFile(), targetRoot);
        } else {
            throw new IOException("不支持的站点资源协议: " + resourceRoot.getProtocol());
        }
        logger.info("前端站点资源已释放: {}（{} 个文件）", targetRoot, copied);
    }

    private static int copyDirectory(Path sourceRoot, Path targetRoot) throws IOException {
        if (!Files.isDirectory(sourceRoot)) {
            throw new IOException("站点资源目录不存在: " + sourceRoot);
        }
        try (var paths = Files.walk(sourceRoot)) {
            return paths.filter(Files::isRegularFile)
                    .mapToInt(source -> {
                        try {
                            Path relative = sourceRoot.relativize(source);
                            copyFile(source, targetRoot, relative);
                            return 1;
                        } catch (IOException e) {
                            throw new SiteCopyException(e);
                        }
                    })
                    .sum();
        } catch (SiteCopyException e) {
            throw e.cause;
        }
    }

    private static int copyJar(JarFile jar, Path targetRoot) throws IOException {
        Objects.requireNonNull(jar, "jar");
        int copied = 0;
        String prefix = RESOURCE_ROOT + "/";
        try (jar) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().startsWith(prefix)) {
                    continue;
                }
                Path relative = Path.of(entry.getName().substring(prefix.length()));
                try (InputStream input = jar.getInputStream(entry)) {
                    Path target = targetPath(targetRoot, relative);
                    Files.createDirectories(target.getParent());
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                }
                copied++;
            }
        }
        return copied;
    }

    private static void copyFile(Path source, Path targetRoot, Path relative) throws IOException {
        Path target = targetPath(targetRoot, relative);
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static Path targetPath(Path targetRoot, Path relative) throws IOException {
        Path target = targetRoot.resolve(relative).normalize();
        if (!target.startsWith(targetRoot)) {
            throw new IOException("非法站点资源路径: " + relative);
        }
        return target;
    }

    private static final class SiteCopyException extends RuntimeException {
        private final IOException cause;

        private SiteCopyException(IOException cause) {
            super(cause);
            this.cause = cause;
        }
    }
}
