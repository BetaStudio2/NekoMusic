package com.neko.music.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicRecognitionPersistenceTest {
    @TempDir
    Path tempDirectory;

    @Test
    void savesAndRestoresDirectlyQueryableInvertedIndex() throws Exception {
        MusicRecognitionService.Track first = track(1, "First");
        MusicRecognitionService.Track second = track(2, "Second");
        List<MusicRecognitionService.CatalogEntry> catalog = List.of(
                entry(first, "1.mp3", 101L, 1_000L),
                entry(second, "2.flac", 202L, 2_000L));

        AudioFingerprintEngine.Fingerprint firstFingerprint = fingerprint(
                landmark(100, 10), landmark(200, 20), landmark(300, 30));
        AudioFingerprintEngine.Fingerprint secondFingerprint = fingerprint(
                landmark(400, 10), landmark(500, 20));
        AudioFingerprintEngine.Index index = AudioFingerprintEngine.Index.build(Map.of(
                1, firstFingerprint,
                2, secondFingerprint));
        MusicRecognitionService.IndexSnapshot snapshot = new MusicRecognitionService.IndexSnapshot(
                index, Map.of(1, first, 2, second), catalog, System.currentTimeMillis());
        MusicRecognitionService.FullIndexDiskCache cache = cache();

        cache.save(snapshot);
        MusicRecognitionService.IndexSnapshot restored = cache.load(catalog).orElseThrow();

        assertEquals(2, restored.index().musicCount());
        assertEquals(index.uniqueHashCount(), restored.index().uniqueHashCount());
        assertEquals("First", restored.tracks().get(1).title());
        AudioFingerprintEngine.Fingerprint query = fingerprint(
                landmark(100, 1), landmark(200, 11), landmark(300, 21));
        AudioFingerprintEngine.Match match = restored.index().findBest(query, 2, 0.1).orElseThrow();
        assertEquals(1, match.musicId());
        assertEquals(9, match.offsetFrames());
    }

    @Test
    void rejectsPersistedIndexWhenAudioManifestChanges() throws Exception {
        MusicRecognitionService.Track track = track(1, "First");
        List<MusicRecognitionService.CatalogEntry> original = List.of(entry(track, "1.mp3", 101L, 1_000L));
        AudioFingerprintEngine.Index index = AudioFingerprintEngine.Index.build(
                Map.of(1, fingerprint(landmark(100, 10), landmark(200, 20))));
        MusicRecognitionService.FullIndexDiskCache cache = cache();
        cache.save(new MusicRecognitionService.IndexSnapshot(
                index, Map.of(1, track), original, System.currentTimeMillis()));

        List<MusicRecognitionService.CatalogEntry> changed = List.of(entry(track, "1.mp3", 102L, 1_000L));

        assertTrue(cache.load(original).isPresent());
        assertFalse(cache.load(changed).isPresent());
    }

    @Test
    void ignoresCorruptPersistentIndex() throws Exception {
        MusicRecognitionService.FullIndexDiskCache cache = cache();
        Files.write(tempDirectory.resolve("library.nfi"), new byte[]{1, 2, 3, 4});

        assertFalse(cache.load(List.of()).isPresent());
    }

    private MusicRecognitionService.FullIndexDiskCache cache() {
        return new MusicRecognitionService.FullIndexDiskCache(tempDirectory.resolve("library.nfi"));
    }

    private static MusicRecognitionService.Track track(int id, String title) {
        return new MusicRecognitionService.Track(id, title, "Artist", "Album", 180, "", "", "2026-09-05T00:00:00Z");
    }

    private static MusicRecognitionService.CatalogEntry entry(
            MusicRecognitionService.Track track,
            String fileName,
            long size,
            long modifiedAtMillis) {
        return new MusicRecognitionService.CatalogEntry(
                track, new MusicRecognitionService.AudioAsset(fileName, size, modifiedAtMillis));
    }

    private static AudioFingerprintEngine.Landmark landmark(int hash, int frame) {
        return new AudioFingerprintEngine.Landmark(hash, frame);
    }

    private static AudioFingerprintEngine.Fingerprint fingerprint(AudioFingerprintEngine.Landmark... landmarks) {
        return new AudioFingerprintEngine.Fingerprint(List.of(landmarks), 110_250L);
    }
}
