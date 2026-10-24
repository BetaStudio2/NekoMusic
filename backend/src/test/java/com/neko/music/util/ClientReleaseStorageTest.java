package com.neko.music.util;

import com.neko.music.service.AppReleaseService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
