package com.neko.music.util;

import com.neko.music.service.AppReleaseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientReleaseStorageTest {

    @Test
    void watchApkUsesIndependentVersionAndSeparateFileName() {
        assertEquals("NekoMusic-watch-1.4.0-12.apk",
                ClientReleaseStorage.watchApkFileName("1.4.0-12"));

        AppReleaseService.AppRelease release =
                new AppReleaseService.AppRelease("20260207-36", "2026.207.6", "1.4.0-12");
        assertEquals("NekoMusic-watch-1.4.0-12.apk",
                ClientReleaseStorage.expectedFileNameForPlatform("watch", release));
        assertNull(ClientReleaseStorage.watchApkFileName(null));
    }

    @Test
    void watchPlatformOnlyAcceptsApk() {
        assertEquals(".apk", ClientReleaseStorage.requiredExtensionForPlatform("watch"));
        ClientReleaseStorage.validateSourceFileForPlatform("watch", "watch-release.APK", 1024);
        assertThrows(IllegalArgumentException.class,
                () -> ClientReleaseStorage.validateSourceFileForPlatform("watch", "watch-release.exe", 1024));
        assertNull(ClientReleaseStorage.requiredExtensionForPlatform("wear"));
    }

    @Test
    void platformCleanupKeepsNewAndPublishedPackagesOnly(@TempDir Path dir) throws Exception {
        Path oldAndroid = Files.writeString(dir.resolve("20260101-1.apk"), "old");
        Path publishedAndroid = Files.writeString(dir.resolve("20260201-2.apk"), "published");
        Path newAndroid = Files.writeString(dir.resolve("20260207-3.apk"), "new");
        Path watch = Files.writeString(dir.resolve("NekoMusic-watch-1.4.0.apk"), "watch");

        int removed = ClientReleaseStorage.deleteObsoletePlatformPackages(
                dir, "android", Set.of(publishedAndroid.getFileName().toString(), newAndroid.getFileName().toString()));

        assertEquals(1, removed);
        assertFalse(Files.exists(oldAndroid));
        assertTrue(Files.exists(publishedAndroid));
        assertTrue(Files.exists(newAndroid));
        assertTrue(Files.exists(watch));
    }

    @Test
    void releaseCleanupRemovesEveryOlderPlatformPackage(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("20260101-1.apk"), "old android");
        Files.writeString(dir.resolve("NekoMusic-watch-1.3.0.apk"), "old watch");
        Files.writeString(dir.resolve("Neko歌姬计划 Setup 2026.101.1.exe"), "old windows");
        Files.writeString(dir.resolve("NekoMusic_2026.101.1_amd64.deb"), "old linux");
        Files.writeString(dir.resolve("Neko歌姬计划2026.101.1.pkg"), "old mac");

        Files.writeString(dir.resolve("20260207-3.apk"), "new android");
        Files.writeString(dir.resolve("NekoMusic-watch-1.4.0.apk"), "new watch");
        Files.writeString(dir.resolve("Neko歌姬计划 Setup 2026.207.6.exe"), "new windows");
        Files.writeString(dir.resolve("NekoMusic_2026.207.6_amd64.deb"), "new linux");
        Files.writeString(dir.resolve("Neko歌姬计划2026.207.6.pkg"), "new mac");
        Path unrelated = Files.writeString(dir.resolve("keep.txt"), "unrelated");

        int removed = ClientReleaseStorage.deleteObsoleteReleasePackages(
                dir, "20260207-3", "2026.207.6", "1.4.0");

        assertEquals(5, removed);
        assertTrue(Files.exists(dir.resolve("20260207-3.apk")));
        assertTrue(Files.exists(dir.resolve("NekoMusic-watch-1.4.0.apk")));
        assertTrue(Files.exists(dir.resolve("Neko歌姬计划 Setup 2026.207.6.exe")));
        assertTrue(Files.exists(dir.resolve("NekoMusic_2026.207.6_amd64.deb")));
        assertTrue(Files.exists(dir.resolve("Neko歌姬计划2026.207.6.pkg")));
        assertTrue(Files.exists(unrelated));
    }
}
