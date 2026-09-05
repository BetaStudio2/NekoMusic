package com.neko.music.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioFingerprintEngineTest {
    @Test
    void matchesAStableSyntheticTrackAndRejectsUnrelatedTrack() {
        AudioFingerprintEngine engine = new AudioFingerprintEngine();
        short[] track = waveform(18, 0);
        short[] other = waveform(18, 1);
        AudioFingerprintEngine.Fingerprint trackFingerprint = engine.fingerprint(track);
        AudioFingerprintEngine.Fingerprint otherFingerprint = engine.fingerprint(other);
        AudioFingerprintEngine.Index index = AudioFingerprintEngine.Index.build(
                Map.of(11, trackFingerprint, 22, otherFingerprint));

        AudioFingerprintEngine.Fingerprint query = engine.fingerprint(slice(track, 4, 8));
        AudioFingerprintEngine.Match match = index.findBest(query, 3, 0.05).orElseThrow();
        assertEquals(11, match.musicId());
        assertTrue(match.confidence() >= 0.05);

        assertTrue(index.findBest(engine.fingerprint(new short[8 * AudioFingerprintEngine.SAMPLE_RATE]), 3, 0.05).isEmpty());
    }

    @Test
    void indexDeduplicatesLandmarksPerTrack() {
        AudioFingerprintEngine engine = new AudioFingerprintEngine();
        AudioFingerprintEngine.Fingerprint fp = engine.fingerprint(waveform(8, 0));
        AudioFingerprintEngine.Index index = AudioFingerprintEngine.Index.build(new HashMap<>(Map.of(1, fp, 2, fp)));
        assertEquals(2, index.musicCount());
        assertTrue(index.uniqueHashCount() > 0);
    }

    private static short[] waveform(int seconds, int variant) {
        int count = seconds * AudioFingerprintEngine.SAMPLE_RATE;
        short[] result = new short[count];
        double[] frequencies = variant == 0
                ? new double[]{220, 330, 440, 554}
                : variant == 1
                ? new double[]{247, 371, 494, 622}
                : new double[]{173, 281, 419, 691};
        for (int i = 0; i < count; i++) {
            double t = i / (double) AudioFingerprintEngine.SAMPLE_RATE;
            double sweep = Math.sin(2 * Math.PI * (40 + 7 * t) * t);
            double signal = 0;
            for (int j = 0; j < frequencies.length; j++) {
                signal += Math.sin(2 * Math.PI * (frequencies[j] + (j + 1) * 2 * t) * t + sweep * 0.4) / frequencies.length;
            }
            result[i] = (short) Math.round(signal * 20_000);
        }
        return result;
    }

    private static short[] slice(short[] source, int startSeconds, int durationSeconds) {
        int start = startSeconds * AudioFingerprintEngine.SAMPLE_RATE;
        int length = durationSeconds * AudioFingerprintEngine.SAMPLE_RATE;
        short[] result = new short[length];
        System.arraycopy(source, start, result, 0, length);
        return result;
    }
}
