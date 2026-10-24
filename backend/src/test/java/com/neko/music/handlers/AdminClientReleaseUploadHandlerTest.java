package com.neko.music.handlers;

import com.neko.music.service.AppReleaseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 上传安装包时的平台校验分流：手表包不做 APK 校验，手机包仍必须通过。 */
class AdminClientReleaseUploadHandlerTest {

    private static final AppReleaseService.AppRelease RELEASE =
            new AppReleaseService.AppRelease("20260207-36", "2026.207.6", "1.4.0");

    @Test
    void watchUploadSkipsApkValidation(@TempDir Path dir) throws Exception {
        Path watchApk = Files.writeString(dir.resolve("watch.apk"), "not a real apk");

        assertDoesNotThrow(() -> AdminClientReleaseUploadHandler.validateUploadedArtifact(
                "watch", watchApk, RELEASE));
    }

    @Test
    void androidUploadStillValidatesApk(@TempDir Path dir) throws Exception {
        Path androidApk = Files.writeString(dir.resolve("android.apk"), "not a real apk");

        assertThrows(IllegalArgumentException.class,
                () -> AdminClientReleaseUploadHandler.validateUploadedArtifact("android", androidApk, RELEASE));
    }
}
