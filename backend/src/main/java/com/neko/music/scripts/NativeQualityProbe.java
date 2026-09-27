package com.neko.music.scripts;

import com.neko.music.nativeaudio.NativeAudioQuality;

public final class NativeQualityProbe {
    private NativeQualityProbe() {}

    public static void main(String[] args) throws Exception {
        long[] values = NativeAudioQuality.probe(args[0]);
        if (values == null || values.length < 5) throw new IllegalStateException("probe failed");
        String quality = switch ((int) values[0]) {
            case 1 -> "standard";
            case 2 -> "hq";
            case 3 -> "sq";
            case 4 -> "hires";
            default -> "hq";
        };
        System.out.printf("%s,%d,%d,%d,%d%n", quality, values[1], values[2], values[3], values[4]);
    }
}
