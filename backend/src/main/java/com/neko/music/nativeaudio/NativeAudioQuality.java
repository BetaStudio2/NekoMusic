package com.neko.music.nativeaudio;

import java.nio.file.Files;
import java.nio.file.Path;

/** Native audio probing and transcoding bridge. */
public final class NativeAudioQuality {
    private static final boolean AVAILABLE = loadLibrary();

    private NativeAudioQuality() {
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public static native long[] probe(String path);

    public static native int transcode(String ffmpegPath, String source, String target, int bitrateKbps);

    private static boolean loadLibrary() {
        String configured = System.getProperty("neko.audio.native");
        if (configured != null && !configured.isBlank()) {
            try {
                System.load(Path.of(configured).toAbsolutePath().toString());
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }
        for (Path local : new Path[] {
                Path.of(System.getProperty("user.dir"), "native", "libneko_audio_quality.so"),
                Path.of(System.getProperty("user.dir"), "backend", "native", "libneko_audio_quality.so")
        }) {
            if (Files.isRegularFile(local)) {
                try {
                    System.load(local.toAbsolutePath().toString());
                    return true;
                } catch (Throwable ignored) {
                    // Try the next conventional location.
                }
            }
        }
        try {
            System.loadLibrary("neko_audio_quality");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
