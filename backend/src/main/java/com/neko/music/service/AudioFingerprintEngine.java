package com.neko.music.service;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Pure Java acoustic fingerprinting based on spectral peaks and landmark pairs.
 * Input is signed 16-bit little-endian mono PCM at {@value #SAMPLE_RATE} Hz.
 */
public final class AudioFingerprintEngine {
    public static final int SAMPLE_RATE = 11_025;
    public static final int FFT_SIZE = 2_048;
    public static final int HOP_SIZE = 1_024;
    // Bumped after introducing bounded landmark/index storage so older,
    // potentially multi-gigabyte persistent indexes are never loaded.
    public static final int ALGORITHM_VERSION = 2;

    private static final int TEMPORAL_RADIUS = 2;
    private static final int FREQUENCY_QUANTIZATION_BINS = 4;
    private static final double MIN_FRAME_RMS = 0.000_01d;
    private static final int MAX_VOTE_COMPARISONS = 2_000_000;
    /** Bounds the in-memory inverted index for very long tracks and large libraries. */
    private static final int MAX_INDEX_POSTINGS_PER_HASH = 4_096;
    private static final long MAX_INDEX_TOTAL_POSTINGS = 8_000_000L;
    private static final int[][] TARGET_WINDOWS = {
            {1, 4}, {5, 9}, {10, 17}, {18, 30}
    };
    private static final double[] BAND_EDGES_HZ = {
            80d, 250d, 520d, 1_000d, 2_000d, 4_800d
    };

    private final double[] hannWindow = new double[FFT_SIZE];
    private final int[] bandStartBins = new int[BAND_EDGES_HZ.length - 1];
    private final int[] bandEndBins = new int[BAND_EDGES_HZ.length - 1];

    public AudioFingerprintEngine() {
        for (int i = 0; i < hannWindow.length; i++) {
            hannWindow[i] = 0.5d - 0.5d * Math.cos(2d * Math.PI * i / (FFT_SIZE - 1));
        }
        for (int i = 0; i < bandStartBins.length; i++) {
            bandStartBins[i] = Math.max(1, frequencyToBin(BAND_EDGES_HZ[i]));
            bandEndBins[i] = Math.min(FFT_SIZE / 2 - 1, frequencyToBin(BAND_EDGES_HZ[i + 1]));
        }
    }

    public Fingerprint fingerprint(short[] samples) {
        if (samples == null || samples.length == 0) {
            return new Fingerprint(List.of(), 0);
        }
        byte[] pcm = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            pcm[i * 2] = (byte) (samples[i] & 0xff);
            pcm[i * 2 + 1] = (byte) ((samples[i] >>> 8) & 0xff);
        }
        try {
            return fingerprint(new ByteArrayInputStream(pcm), samples.length);
        } catch (IOException e) {
            throw new IllegalStateException("Unexpected in-memory PCM read failure", e);
        }
    }

    /**
     * Fingerprints a PCM stream without retaining the decoded waveform in memory.
     *
     * @throws SampleLimitExceededException when the stream contains more than {@code maxSamples}
     */
    public Fingerprint fingerprint(InputStream pcmInput, long maxSamples) throws IOException {
        if (maxSamples < 1) {
            throw new IllegalArgumentException("maxSamples must be positive");
        }
        LittleEndianPcmReader reader = new LittleEndianPcmReader(pcmInput, maxSamples);
        short[] frame = new short[FFT_SIZE];
        int initial = readFully(reader, frame, 0, frame.length);
        if (initial < FFT_SIZE) {
            return new Fingerprint(List.of(), reader.samplesRead());
        }

        List<FrameSpectrum> spectra = new ArrayList<>();
        double[] real = new double[FFT_SIZE];
        double[] imaginary = new double[FFT_SIZE];
        while (true) {
            spectra.add(analyzeFrame(frame, real, imaginary));

            System.arraycopy(frame, HOP_SIZE, frame, 0, FFT_SIZE - HOP_SIZE);
            int read = readFully(reader, frame, FFT_SIZE - HOP_SIZE, HOP_SIZE);
            if (read < HOP_SIZE) {
                break;
            }
        }

        return new Fingerprint(createLandmarks(findPeaks(spectra)), reader.samplesRead());
    }

    private FrameSpectrum analyzeFrame(short[] frame, double[] real, double[] imaginary) {
        double squareSum = 0d;
        for (int i = 0; i < FFT_SIZE; i++) {
            double sample = frame[i] / 32768d;
            squareSum += sample * sample;
            real[i] = sample * hannWindow[i];
            imaginary[i] = 0d;
        }
        double rms = Math.sqrt(squareSum / FFT_SIZE);
        if (rms < MIN_FRAME_RMS) {
            return FrameSpectrum.silent(bandStartBins.length);
        }

        fft(real, imaginary);
        int[] peakBins = new int[bandStartBins.length];
        double[] strengths = new double[bandStartBins.length];
        for (int band = 0; band < bandStartBins.length; band++) {
            int strongestBin = bandStartBins[band];
            double strongestPower = -1d;
            for (int bin = bandStartBins[band]; bin <= bandEndBins[band]; bin++) {
                double power = real[bin] * real[bin] + imaginary[bin] * imaginary[bin];
                if (power > strongestPower) {
                    strongestPower = power;
                    strongestBin = bin;
                }
            }
            peakBins[band] = strongestBin;
            strengths[band] = Math.log1p(Math.max(0d, strongestPower));
        }
        return new FrameSpectrum(peakBins, strengths, false);
    }

    private List<SpectralPeak> findPeaks(List<FrameSpectrum> spectra) {
        if (spectra.isEmpty()) {
            return List.of();
        }
        List<SpectralPeak> peaks = new ArrayList<>();
        for (int frame = 0; frame < spectra.size(); frame++) {
            FrameSpectrum candidateFrame = spectra.get(frame);
            if (candidateFrame.silent) {
                continue;
            }
            for (int band = 0; band < bandStartBins.length; band++) {
                double candidate = candidateFrame.strengths[band];
                boolean isMaximum = candidate > 0d;
                boolean tied = false;
                int from = Math.max(0, frame - TEMPORAL_RADIUS);
                int to = Math.min(spectra.size() - 1, frame + TEMPORAL_RADIUS);
                for (int otherFrame = from; otherFrame <= to && isMaximum; otherFrame++) {
                    if (otherFrame == frame) {
                        continue;
                    }
                    double other = spectra.get(otherFrame).strengths[band];
                    if (other > candidate + 1e-9d) {
                        isMaximum = false;
                    } else if (Math.abs(other - candidate) <= 1e-9d) {
                        tied = true;
                    }
                }
                if (isMaximum && (!tied || frame % (TEMPORAL_RADIUS * 2 + 1) == 0)) {
                    peaks.add(new SpectralPeak(frame, candidateFrame.peakBins[band], candidate));
                }
            }
        }
        peaks.sort(Comparator.comparingInt(SpectralPeak::timeFrame)
                .thenComparingInt(SpectralPeak::frequencyBin));
        return peaks;
    }

    private List<Landmark> createLandmarks(List<SpectralPeak> peaks) {
        if (peaks.size() < 2) {
            return List.of();
        }
        List<Landmark> landmarks = new ArrayList<>(peaks.size() * TARGET_WINDOWS.length);
        Set<Long> unique = new HashSet<>();
        for (int anchorIndex = 0; anchorIndex < peaks.size(); anchorIndex++) {
            SpectralPeak anchor = peaks.get(anchorIndex);
            for (int[] window : TARGET_WINDOWS) {
                SpectralPeak strongest = null;
                SpectralPeak secondStrongest = null;
                for (int targetIndex = anchorIndex + 1; targetIndex < peaks.size(); targetIndex++) {
                    SpectralPeak target = peaks.get(targetIndex);
                    int delta = target.timeFrame - anchor.timeFrame;
                    if (delta > window[1]) {
                        break;
                    }
                    if (delta < window[0]) {
                        continue;
                    }
                    if (strongest == null || target.strength > strongest.strength) {
                        secondStrongest = strongest;
                        strongest = target;
                    } else if (secondStrongest == null || target.strength > secondStrongest.strength) {
                        secondStrongest = target;
                    }
                }
                addLandmark(anchor, strongest, landmarks, unique);
                addLandmark(anchor, secondStrongest, landmarks, unique);
            }
        }
        landmarks.sort(Comparator.comparingInt(Landmark::timeFrame).thenComparingInt(Landmark::hash));
        return landmarks;
    }

    private static void addLandmark(
            SpectralPeak anchor,
            SpectralPeak target,
            List<Landmark> output,
            Set<Long> unique) {
        if (target == null) {
            return;
        }
        int delta = target.timeFrame - anchor.timeFrame;
        int anchorFrequency = Math.min(255, anchor.frequencyBin / FREQUENCY_QUANTIZATION_BINS);
        int targetFrequency = Math.min(255, target.frequencyBin / FREQUENCY_QUANTIZATION_BINS);
        int quantizedDelta = Math.min(63, (delta + 1) / 2);
        int hash = anchorFrequency | (targetFrequency << 8) | (quantizedDelta << 16);
        long key = ((long) anchor.timeFrame << 32) | (hash & 0xffff_ffffL);
        if (unique.add(key)) {
            output.add(new Landmark(hash, anchor.timeFrame));
        }
    }

    private int frequencyToBin(double frequencyHz) {
        return (int) Math.round(frequencyHz * FFT_SIZE / SAMPLE_RATE);
    }

    private static int readFully(LittleEndianPcmReader reader, short[] target, int offset, int length)
            throws IOException {
        int total = 0;
        while (total < length) {
            int read = reader.read(target, offset + total, length - total);
            if (read < 0) {
                break;
            }
            total += read;
        }
        return total;
    }

    private static void fft(double[] real, double[] imaginary) {
        int n = real.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            while ((j & bit) != 0) {
                j ^= bit;
                bit >>= 1;
            }
            j ^= bit;
            if (i < j) {
                double realSwap = real[i];
                real[i] = real[j];
                real[j] = realSwap;
                double imaginarySwap = imaginary[i];
                imaginary[i] = imaginary[j];
                imaginary[j] = imaginarySwap;
            }
        }

        for (int length = 2; length <= n; length <<= 1) {
            double angle = -2d * Math.PI / length;
            double rootReal = Math.cos(angle);
            double rootImaginary = Math.sin(angle);
            for (int start = 0; start < n; start += length) {
                double factorReal = 1d;
                double factorImaginary = 0d;
                for (int j = 0; j < length / 2; j++) {
                    int even = start + j;
                    int odd = even + length / 2;
                    double oddReal = real[odd] * factorReal - imaginary[odd] * factorImaginary;
                    double oddImaginary = real[odd] * factorImaginary + imaginary[odd] * factorReal;
                    real[odd] = real[even] - oddReal;
                    imaginary[odd] = imaginary[even] - oddImaginary;
                    real[even] += oddReal;
                    imaginary[even] += oddImaginary;

                    double nextFactorReal = factorReal * rootReal - factorImaginary * rootImaginary;
                    factorImaginary = factorReal * rootImaginary + factorImaginary * rootReal;
                    factorReal = nextFactorReal;
                }
            }
        }
    }

    public record Landmark(int hash, int timeFrame) {
        public Landmark {
            if (timeFrame < 0) {
                throw new IllegalArgumentException("timeFrame cannot be negative");
            }
        }
    }

    public record Fingerprint(List<Landmark> landmarks, long sampleCount) {
        public Fingerprint {
            landmarks = landmarks == null ? List.of() : List.copyOf(landmarks);
            if (sampleCount < 0) {
                throw new IllegalArgumentException("sampleCount cannot be negative");
            }
        }

        public double durationSeconds() {
            return sampleCount / (double) SAMPLE_RATE;
        }
    }

    public record Match(
            int musicId,
            int alignedLandmarks,
            double confidence,
            int offsetFrames,
            int runnerUpLandmarks) {

        public double offsetSeconds() {
            return offsetFrames * HOP_SIZE / (double) SAMPLE_RATE;
        }
    }

    /** Immutable inverted landmark index. */
    public static final class Index {
        private final Map<Integer, long[]> postings;
        private final int musicCount;

        private Index(Map<Integer, long[]> postings, int musicCount) {
            this.postings = postings;
            this.musicCount = musicCount;
        }

        public static Index build(Map<Integer, Fingerprint> fingerprints) {
            if (fingerprints == null || fingerprints.isEmpty()) {
                return new Index(Map.of(), 0);
            }
            Builder builder = new Builder();
            fingerprints.forEach(builder::add);
            return builder.build();
        }

        public static Builder builder() {
            return new Builder();
        }

        /** Creates a builder that keeps untouched posting arrays from an existing index. */
        public static Builder builder(Index base) {
            return new Builder(base);
        }

        /** Incrementally builds an index without retaining every track fingerprint. */
        public static final class Builder {
            Map<Integer, LongList> mutable = new HashMap<>();
            final Map<Integer, long[]> basePostings;
            int indexedMusic = 0;
            long totalPostings = 0L;

            private Builder() {
                basePostings = Map.of();
            }

            private Builder(Index base) {
                basePostings = base == null ? Map.of() : base.postings;
                indexedMusic = base == null ? 0 : base.musicCount;
                for (long[] values : basePostings.values()) {
                    totalPostings += values.length;
                }
            }

            public void add(int musicId, Fingerprint fingerprint) {
                if (musicId <= 0 || fingerprint == null || fingerprint.landmarks().isEmpty()) {
                    return;
                }
                int postingsBeforeTrack = (int) Math.min(Integer.MAX_VALUE, totalPostings);
                Set<Long> uniqueForTrack = new HashSet<>();
                for (Landmark landmark : fingerprint.landmarks()) {
                    if (totalPostings >= MAX_INDEX_TOTAL_POSTINGS) {
                        break;
                    }
                    long duplicateKey = ((long) landmark.hash << 32) | (landmark.timeFrame & 0xffff_ffffL);
                    if (!uniqueForTrack.add(duplicateKey)) {
                        continue;
                    }
                    long posting = ((long) musicId << 32) | (landmark.timeFrame & 0xffff_ffffL);
                    LongList postings = mutable.computeIfAbsent(landmark.hash,
                            hash -> new LongList(basePostings.get(hash)));
                    if (postings.size() >= MAX_INDEX_POSTINGS_PER_HASH) {
                        continue;
                    }
                    postings.add(posting);
                    totalPostings++;
                }
                if (totalPostings > postingsBeforeTrack) {
                    indexedMusic++;
                }
                if (totalPostings >= MAX_INDEX_TOTAL_POSTINGS) {
                    return;
                }
            }

            public Index build() {
                Map<Integer, long[]> immutable = new HashMap<>(basePostings);
                mutable.forEach((hash, values) -> immutable.put(hash, values.toArray()));
                mutable.clear();
                return new Index(Map.copyOf(immutable), indexedMusic);
            }
        }

        public int musicCount() {
            return musicCount;
        }

        public int uniqueHashCount() {
            return postings.size();
        }

        /** Restores arrays owned by the persistence loader; callers must not mutate them afterwards. */
        static Index restore(Map<Integer, long[]> persistedPostings, int musicCount) {
            if (musicCount < 0) {
                throw new IllegalArgumentException("musicCount cannot be negative");
            }
            if (persistedPostings == null || persistedPostings.isEmpty()) {
                return new Index(Map.of(), musicCount);
            }
            Map<Integer, long[]> restored = new HashMap<>(persistedPostings.size());
            persistedPostings.forEach((hash, values) -> {
                if (values == null || values.length == 0) {
                    throw new IllegalArgumentException("posting list cannot be empty");
                }
                restored.put(hash, values);
            });
            return new Index(Map.copyOf(restored), musicCount);
        }

        /** Package-private read-only view used only by the atomic persistence writer. */
        Map<Integer, long[]> postingsView() {
            return postings;
        }

        public Optional<Match> findBest(Fingerprint query, int minimumAlignedLandmarks, double minimumConfidence) {
            if (query == null || query.landmarks().isEmpty() || postings.isEmpty()) {
                return Optional.empty();
            }

            int maximumPostingsPerHash = Math.max(512, musicCount * 64);
            Map<Long, Integer> offsetVotes = new HashMap<>();
            Set<Long> uniqueQueryLandmarks = new HashSet<>();
            int comparisons = 0;
            for (Landmark landmark : query.landmarks()) {
                long queryKey = ((long) landmark.hash << 32) | (landmark.timeFrame & 0xffff_ffffL);
                if (!uniqueQueryLandmarks.add(queryKey)) {
                    continue;
                }
                long[] matches = postings.get(landmark.hash);
                if (matches == null || matches.length > maximumPostingsPerHash) {
                    continue;
                }
                for (long posting : matches) {
                    int musicId = (int) (posting >>> 32);
                    int indexedTime = (int) posting;
                    int offset = indexedTime - landmark.timeFrame;
                    long voteKey = ((long) musicId << 32) | (offset & 0xffff_ffffL);
                    offsetVotes.merge(voteKey, 1, Integer::sum);
                    comparisons++;
                    if (comparisons >= MAX_VOTE_COMPARISONS) {
                        break;
                    }
                }
                if (comparisons >= MAX_VOTE_COMPARISONS) {
                    break;
                }
            }
            if (offsetVotes.isEmpty()) {
                return Optional.empty();
            }

            Map<Integer, VotePeak> bestByMusic = new LinkedHashMap<>();
            for (Map.Entry<Long, Integer> entry : offsetVotes.entrySet()) {
                int musicId = (int) (entry.getKey() >>> 32);
                int offset = (int) (long) entry.getKey();
                int smoothedVotes = entry.getValue()
                        + offsetVotes.getOrDefault(packVoteKey(musicId, offset - 1), 0)
                        + offsetVotes.getOrDefault(packVoteKey(musicId, offset + 1), 0);
                VotePeak previous = bestByMusic.get(musicId);
                if (previous == null || smoothedVotes > previous.votes) {
                    bestByMusic.put(musicId, new VotePeak(offset, smoothedVotes));
                }
            }

            List<Map.Entry<Integer, VotePeak>> ranked = new ArrayList<>(bestByMusic.entrySet());
            ranked.sort((left, right) -> Integer.compare(right.getValue().votes, left.getValue().votes));
            Map.Entry<Integer, VotePeak> best = ranked.getFirst();
            int runnerUp = ranked.size() > 1 ? ranked.get(1).getValue().votes : 0;
            double confidence = Math.min(1d, best.getValue().votes / (double) query.landmarks().size());
            boolean sufficientlyDistinct = runnerUp == 0
                    || best.getValue().votes >= runnerUp + 3
                    || best.getValue().votes >= Math.ceil(runnerUp * 1.15d);
            if (best.getValue().votes < minimumAlignedLandmarks
                    || confidence < minimumConfidence
                    || !sufficientlyDistinct) {
                return Optional.empty();
            }
            return Optional.of(new Match(
                    best.getKey(),
                    best.getValue().votes,
                    confidence,
                    best.getValue().offset,
                    runnerUp));
        }

        private static long packVoteKey(int musicId, int offset) {
            return ((long) musicId << 32) | (offset & 0xffff_ffffL);
        }
    }

    public static final class SampleLimitExceededException extends IOException {
        public SampleLimitExceededException(long maxSamples) {
            super("PCM sample limit exceeded: " + maxSamples);
        }
    }

    private record FrameSpectrum(int[] peakBins, double[] strengths, boolean silent) {
        private static FrameSpectrum silent(int bands) {
            return new FrameSpectrum(new int[bands], new double[bands], true);
        }
    }

    private record SpectralPeak(int timeFrame, int frequencyBin, double strength) {
    }

    private record VotePeak(int offset, int votes) {
    }

    private static final class LittleEndianPcmReader {
        private final InputStream input;
        private final long maxSamples;
        private final byte[] byteBuffer = new byte[8_192];
        private int bytePosition;
        private int byteLimit;
        private long samplesRead;

        private LittleEndianPcmReader(InputStream input, long maxSamples) {
            this.input = input instanceof BufferedInputStream ? input : new BufferedInputStream(input, 32 * 1_024);
            this.maxSamples = maxSamples;
        }

        private int read(short[] target, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            int count = 0;
            while (count < length) {
                int low = readByte();
                if (low < 0) {
                    return count == 0 ? -1 : count;
                }
                int high = readByte();
                if (high < 0) {
                    throw new IOException("Odd byte count in PCM stream");
                }
                if (samplesRead >= maxSamples) {
                    throw new SampleLimitExceededException(maxSamples);
                }
                target[offset + count] = (short) ((high << 8) | low);
                samplesRead++;
                count++;
            }
            return count;
        }

        private int readByte() throws IOException {
            if (bytePosition >= byteLimit) {
                byteLimit = input.read(byteBuffer);
                bytePosition = 0;
                if (byteLimit < 0) {
                    return -1;
                }
            }
            return byteBuffer[bytePosition++] & 0xff;
        }

        private long samplesRead() {
            return samplesRead;
        }
    }

    private static final class LongList {
        private long[] values = new long[16];
        private int size;

        private LongList(long[] initial) {
            if (initial != null && initial.length > 0) {
                values = Arrays.copyOf(initial, Math.max(16, initial.length));
                size = initial.length;
            }
        }

        private int size() {
            return size;
        }

        private void add(long value) {
            if (size == values.length) {
                values = Arrays.copyOf(values, values.length * 2);
            }
            values[size++] = value;
        }

        private long[] toArray() {
            return Arrays.copyOf(values, size);
        }
    }
}
