package com.neko.music.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 从 JAR 内嵌 Noto Sans SC 解压到运行目录，供 FFmpeg subtitles 滤镜 fontsdir 使用。
 */
public final class BundledRenderFontSupport {
    private static final Logger logger = LoggerFactory.getLogger(BundledRenderFontSupport.class);

    private static final String RESOURCE_PATH = "/fonts/NotoSansSC-Regular.otf";
    private static final String FONT_FILE_NAME = "NotoSansSC-Regular.otf";
    public static final String FONT_FAMILY = "Noto Sans SC";

    private static volatile Path cachedFontsDir;

    private BundledRenderFontSupport() {
    }

    public static Path ensureFontsDir() throws IOException {
        if (cachedFontsDir != null && Files.isRegularFile(cachedFontsDir.resolve(FONT_FILE_NAME))) {
            return cachedFontsDir;
        }
        synchronized (BundledRenderFontSupport.class) {
            if (cachedFontsDir != null && Files.isRegularFile(cachedFontsDir.resolve(FONT_FILE_NAME))) {
                return cachedFontsDir;
            }
            Path fontsDir = MusicAssetLocator.baseDir().resolve(".neko").resolve("video-render").resolve("fonts");
            Files.createDirectories(fontsDir);
            Path target = fontsDir.resolve(FONT_FILE_NAME);
            Path marker = fontsDir.resolve(".bundled.ok");

            if (Files.isRegularFile(target) && Files.isRegularFile(marker)) {
                cachedFontsDir = fontsDir;
                return fontsDir;
            }

            InputStream in = BundledRenderFontSupport.class.getResourceAsStream(RESOURCE_PATH);
            if (in == null) {
                throw new IOException("JAR 内未找到渲染字体资源: " + RESOURCE_PATH);
            }
            Path tmp = Files.createTempFile(fontsDir, "font-", ".tmp");
            try (in) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            Files.writeString(marker, FONT_FILE_NAME);
            cachedFontsDir = fontsDir;
            logger.info("视频渲染字体已就绪: {}", target.toAbsolutePath());
            return fontsDir;
        }
    }
}
