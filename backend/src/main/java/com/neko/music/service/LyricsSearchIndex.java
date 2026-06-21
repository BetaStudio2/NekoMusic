package com.neko.music.service;

import com.neko.music.Main;
import com.neko.music.util.LyricsPlainTextExtractor;
import com.neko.music.util.MusicAssetLocator;
import com.neko.music.util.VideoRenderPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 站内歌词搜索索引：仅索引已入库曲目（{@code music} 表存在且 {@code Music/lyrics/{id}.lrc} 有效）。
 * 纯文本缓存目录：{@code /tmp/.neko/lyrics-index/plain}（tmpfs）。
 */
public class LyricsSearchIndex {
    private static final Logger logger = LoggerFactory.getLogger(LyricsSearchIndex.class);

    public static final Path INDEX_ROOT = VideoRenderPaths.NEKO_TMP_ROOT.resolve("lyrics-index");
    public static final Path PLAIN_DIR = INDEX_ROOT.resolve("plain");

    private static final int MIN_QUERY_LEN = 2;
    private static final int NGRAM_SIZE = 2;
    private static final int MAX_RESULTS = 200;
    private static final Pattern LRC_FILE = Pattern.compile("(\\d+)\\.lrc");

    private final Map<Integer, String> plainById = new ConcurrentHashMap<>();
    private final Map<Integer, Set<String>> ngramsById = new ConcurrentHashMap<>();
    private final Map<String, Set<Integer>> postings = new ConcurrentHashMap<>();
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final AtomicBoolean building = new AtomicBoolean(false);

    public boolean isReady() {
        return ready.get();
    }

    public void buildIndexAsync() {
        if (!building.compareAndSet(false, true)) {
            return;
        }
        Thread t = new Thread(this::buildIndex, "lyrics-index-build");
        t.setDaemon(true);
        t.start();
    }

    public synchronized void buildIndex() {
        building.set(true);
        ready.set(false);
        try {
            Files.createDirectories(PLAIN_DIR);
            plainById.clear();
            ngramsById.clear();
            postings.clear();

            Path lyricsDir = MusicAssetLocator.lyricsDir();
            if (!Files.isDirectory(lyricsDir)) {
                logger.info("歌词目录不存在，跳过索引: {}", lyricsDir);
                ready.set(true);
                return;
            }

            int indexed = 0;
            try (Stream<Path> stream = Files.list(lyricsDir)) {
                for (Path lrcFile : stream.filter(Files::isRegularFile).sorted().toList()) {
                    Matcher m = LRC_FILE.matcher(lrcFile.getFileName().toString());
                    if (!m.matches()) {
                        continue;
                    }
                    int musicId = Integer.parseInt(m.group(1));
                    if (!musicExistsInDb(musicId)) {
                        continue;
                    }
                    if (indexOneFile(musicId, lrcFile)) {
                        indexed++;
                    }
                }
            }
            ready.set(true);
            logger.info("站内歌词索引就绪: {} 首, tmpfs={}", indexed, PLAIN_DIR);
        } catch (Exception e) {
            logger.error("站内歌词索引构建失败", e);
        } finally {
            building.set(false);
        }
    }

    /** 该曲目是否有已入库且可展示的有效歌词（非占位）。 */
    public boolean hasIndexedLyrics(int musicId) {
        return musicId > 0 && ready.get() && plainById.containsKey(musicId);
    }

    public record SearchOutcome(List<Integer> ids, int candidateCount, boolean saturated) {
    }

    /** 仅返回已入库且歌词正文包含 query 的 musicId。 */
    public List<Integer> search(String query, int limit) {
        return searchWithStats(query, limit).ids();
    }

    /** 返回歌词搜索命中，并附带候选规模，供调用方过滤过宽泛的查询。 */
    public SearchOutcome searchWithStats(String query, int limit) {
        if (!ready.get() || query == null) {
            return new SearchOutcome(List.of(), 0, false);
        }
        String normalized = LyricsPlainTextExtractor.normalize(query);
        if (normalized.length() < MIN_QUERY_LEN) {
            return new SearchOutcome(List.of(), 0, false);
        }

        List<String> grams = extractNgrams(normalized);
        if (grams.isEmpty()) {
            return new SearchOutcome(List.of(), 0, false);
        }

        Set<Integer> candidates = null;
        for (String gram : grams) {
            Set<Integer> bucket = postings.get(gram);
            if (bucket == null || bucket.isEmpty()) {
                return new SearchOutcome(List.of(), 0, false);
            }
            if (candidates == null) {
                candidates = new HashSet<>(bucket);
            } else {
                candidates.retainAll(bucket);
                if (candidates.isEmpty()) {
                    return new SearchOutcome(List.of(), 0, false);
                }
            }
        }

        int cap = Math.min(Math.max(1, limit), MAX_RESULTS);
        List<Integer> hits = new ArrayList<>();
        boolean saturated = false;
        for (Integer id : candidates) {
            String plain = plainById.get(id);
            if (plain != null && plain.contains(normalized)) {
                if (hits.size() < cap) {
                    hits.add(id);
                } else {
                    saturated = true;
                    break;
                }
            }
        }
        return new SearchOutcome(List.copyOf(hits), candidates.size(), saturated);
    }

    /** 新入库或歌词更新后增量重建（仅当曲目已在库且 lrc 存在）。 */
    public synchronized void rebuildOne(int musicId) {
        if (musicId <= 0 || !musicExistsInDb(musicId)) {
            removeOne(musicId);
            return;
        }
        removeOne(musicId);
        try {
            Files.createDirectories(PLAIN_DIR);
            Path lrc = MusicAssetLocator.findLyricsFile(musicId).orElse(null);
            if (lrc != null) {
                indexOneFile(musicId, lrc);
            }
        } catch (Exception e) {
            logger.warn("重建歌词索引失败 musicId={}: {}", musicId, e.toString());
        }
    }

    private boolean indexOneFile(int musicId, Path lrcFile) throws IOException {
        String plain = LyricsPlainTextExtractor.fromLrc(Files.readString(lrcFile, StandardCharsets.UTF_8));
        if (plain.length() < MIN_QUERY_LEN || LyricsPlainTextExtractor.isPlaceholder(plain)) {
            return false;
        }
        plainById.put(musicId, plain);
        Set<String> grams = Set.copyOf(extractNgrams(plain));
        ngramsById.put(musicId, grams);
        for (String gram : grams) {
            postings.computeIfAbsent(gram, k -> ConcurrentHashMap.newKeySet()).add(musicId);
        }
        Files.writeString(PLAIN_DIR.resolve(musicId + ".txt"), plain, StandardCharsets.UTF_8);
        return true;
    }

    private void removeOne(int musicId) {
        plainById.remove(musicId);
        Set<String> old = ngramsById.remove(musicId);
        if (old != null) {
            for (String gram : old) {
                Set<Integer> bucket = postings.get(gram);
                if (bucket != null) {
                    bucket.remove(musicId);
                    if (bucket.isEmpty()) {
                        postings.remove(gram);
                    }
                }
            }
        }
        try {
            Files.deleteIfExists(PLAIN_DIR.resolve(musicId + ".txt"));
        } catch (IOException ignored) {
        }
    }

    private static boolean musicExistsInDb(int musicId) {
        try (Connection conn = Main.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM music WHERE id = ? LIMIT 1")) {
            ps.setInt(1, musicId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            logger.debug("检查 music 是否存在失败 id={}", musicId);
            return false;
        }
    }

    private static List<String> extractNgrams(String normalized) {
        if (normalized.length() < NGRAM_SIZE) {
            return List.of();
        }
        Set<String> grams = new LinkedHashSet<>();
        for (int i = 0; i <= normalized.length() - NGRAM_SIZE; i++) {
            grams.add(normalized.substring(i, i + NGRAM_SIZE));
        }
        return new ArrayList<>(grams);
    }
}
