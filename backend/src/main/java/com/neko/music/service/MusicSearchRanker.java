package com.neko.music.service;

import com.neko.music.util.ChineseConverter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 曲库搜索相关度评分：先把查询串拆成「歌名 / 歌手」候选词条，再按层级打分。
 *
 * <p>客户端常把歌名与歌手一起提交（{@code 周杰伦，晴天}、{@code 晴天 周杰伦}、
 * {@code 周杰伦-晴天}、{@code 晴天/周杰伦}）。评分按「歌名 &gt; 歌手 &gt; 专辑」分层：
 * 只要歌名（含标题拼音列）命中，就一定排在仅歌手命中的曲目前面；查询串没有分隔符时，
 * 退化成与旧的整串模糊匹配一致的相对顺序。
 *
 * <p>本类不做数据库访问，输入是纯字符串，便于单测覆盖排序规则。
 */
public final class MusicSearchRanker {

    private MusicSearchRanker() {
    }

    /** 一条曲目的可检索字段（拼音列来自 music 表的预计算列）。 */
    public record MusicFields(
            String title, String artist, String album,
            String titlePinyin, String titlePinyinInitials, String titleWordInitials,
            String artistPinyin, String artistPinyinInitials, String artistWordInitials,
            String albumPinyin) {
    }

    /** 词条数上限：避免超长查询把 SQL 条件与角色分配组合撑爆。 */
    private static final int MAX_TERMS = 6;
    /**
     * 词条分隔符：空白与常见的歌名/歌手连接符。
     * 不含英文撇号，避免把 {@code Don't} 这类歌名切坏。
     */
    private static final String TERM_SEPARATORS =
            " \t\u00A0\u3000-\u2013\u2014_+~～/\\|｜,，、;；:：&＆!！?？(（)）[【]】{《}》<>·•";
    /**
     * 「歌名 / 歌手 / 专辑」三级角色的权重（10 的幂）。
     * 单字段得分不超过 100，所以低一级角色拿满分也越不过高一级角色的最低命中分。
     */
    private static final int TITLE_ROLE_MULTIPLIER = 10_000;
    private static final int ARTIST_ROLE_MULTIPLIER = 100;
    /** 英文长句（>= 4 个单词）视为歌词片段，不做「歌名 + 歌手」切分。 */
    private static final int FREE_TEXT_LATIN_WORD_THRESHOLD = 4;
    /** 专辑拼音命中沿用旧实现的固定分（专辑角色本就排在最后）。 */
    private static final int ALBUM_PINYIN_SCORE = 90;

    private static final FieldWeights TITLE_WEIGHTS = new FieldWeights(100, 80, 60, 95, 75, 55);
    private static final FieldWeights ARTIST_WEIGHTS = new FieldWeights(50, 40, 30, 45, 35, 25);
    private static final FieldWeights ALBUM_WEIGHTS = new FieldWeights(20, 15, 10, 18, 13, 8);

    /** 单字段文本匹配各命中类型的加分。 */
    private record FieldWeights(
            int exact, int prefix, int contains,
            int simplifiedExact, int simplifiedPrefix, int simplifiedContains) {
    }

    /**
     * 把查询串切成词条：按分隔符拆分、去空白、去重并限制条数。
     * 单关键词查询返回单元素列表，行为与旧的整串匹配一致。
     */
    public static List<String> splitTerms(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return List.of();
        }
        String trimmed = rawQuery.trim();
        if (isFreeTextPhrase(trimmed)) {
            return List.of(trimmed);
        }

        List<String> terms = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String piece : splitOnSeparators(trimmed)) {
            String term = piece.trim();
            if (term.isEmpty() || !seen.add(term)) {
                continue;
            }
            terms.add(term);
            if (terms.size() >= MAX_TERMS) {
                break;
            }
        }
        return terms.isEmpty() ? List.of(trimmed) : List.copyOf(terms);
    }

    /**
     * 层级评分：枚举「词条 → 角色（歌名 / 歌手 / 专辑）」的全部指派，取分值最高的一种。
     * 这样 {@code 周杰伦，晴天} 与 {@code 晴天/周杰伦} 这类顺序不定的输入都能命中同一首曲目。
     */
    public static int score(MusicFields fields, List<String> terms) {
        if (fields == null || terms == null || terms.isEmpty()) {
            return 0;
        }

        int count = terms.size();
        int[] titleScores = new int[count];
        int[] artistScores = new int[count];
        int[] albumScores = new int[count];
        for (int i = 0; i < count; i++) {
            String term = terms.get(i).toLowerCase(Locale.ROOT);
            titleScores[i] = fieldScore(fields.title(), fields.titlePinyin(),
                    fields.titlePinyinInitials(), fields.titleWordInitials(), term, TITLE_WEIGHTS);
            artistScores[i] = fieldScore(fields.artist(), fields.artistPinyin(),
                    fields.artistPinyinInitials(), fields.artistWordInitials(), term, ARTIST_WEIGHTS);
            albumScores[i] = Math.max(
                    textScore(fields.album(), term, ALBUM_WEIGHTS),
                    pinyinFieldScore(null, fields.albumPinyin(), null, term));
        }
        return bestRoleAssignment(titleScores, artistScores, albumScores);
    }

    /**
     * 整串「元数据相关度」强度（0~170）：只看完整查询串，不看拆分后的词条。
     * 用来判断站内元数据是否已经足够好、无需再搜歌词（阈值与旧实现保持一致）。
     */
    public static int wholeQueryStrength(MusicFields fields, String rawQuery) {
        if (fields == null || rawQuery == null || rawQuery.isBlank()) {
            return 0;
        }

        String trimmed = rawQuery.trim();
        String queryLower = trimmed.toLowerCase(Locale.ROOT);
        String querySimplifiedLower = ChineseConverter.toSimplified(trimmed).toLowerCase(Locale.ROOT);

        int score = textScoreWithSimplified(fields.title(), queryLower, querySimplifiedLower, TITLE_WEIGHTS)
                + textScoreWithSimplified(fields.artist(), queryLower, querySimplifiedLower, ARTIST_WEIGHTS)
                + textScoreWithSimplified(fields.album(), queryLower, querySimplifiedLower, ALBUM_WEIGHTS);
        return Math.max(score, legacyPinyinScore(fields, queryLower));
    }

    private static int fieldScore(
            String value, String pinyin, String pinyinInitials, String wordInitials,
            String term, FieldWeights weights) {
        return Math.max(textScore(value, term, weights),
                pinyinFieldScore(wordInitials, pinyin, pinyinInitials, term));
    }

    private static int textScore(String value, String termLower, FieldWeights weights) {
        return textScoreWithSimplified(value, termLower, ChineseConverter.toSimplified(termLower), weights);
    }

    /**
     * 单字段文本匹配打分：精确 &gt; 前缀 &gt; 子串，并兼容繁简体变体。
     */
    private static int textScoreWithSimplified(
            String value, String termLower, String termSimplifiedLower, FieldWeights weights) {
        if (value == null || value.isEmpty()) {
            return 0;
        }

        String valueLower = value.toLowerCase(Locale.ROOT);
        String valueSimplified = ChineseConverter.toSimplified(value).toLowerCase(Locale.ROOT);

        if (valueLower.equals(termLower)) {
            return weights.exact();
        }
        if (valueLower.startsWith(termLower)) {
            return weights.prefix();
        }
        if (valueLower.contains(termLower)) {
            return weights.contains();
        }
        if (valueSimplified.equals(termLower) || valueLower.equals(termSimplifiedLower)) {
            return weights.simplifiedExact();
        }
        if (valueSimplified.startsWith(termLower) || valueLower.startsWith(termSimplifiedLower)) {
            return weights.simplifiedPrefix();
        }
        if (valueSimplified.contains(termLower) || valueLower.contains(termSimplifiedLower)) {
            return weights.simplifiedContains();
        }
        return 0;
    }

    /**
     * 单字段拼音匹配打分（按优先级取一档，不累加）：
     * 词首字母精确 &gt; 词首字母前缀 &gt; 词首字母子串 &gt; 全拼子串 &gt; 首字母子串。
     */
    private static int pinyinFieldScore(
            String wordInitials, String pinyin, String initials, String termLower) {
        if (wordInitials != null) {
            if (wordInitials.equals(termLower)) {
                return 95;
            }
            if (wordInitials.startsWith(termLower)) {
                return 90;
            }
            if (wordInitials.contains(termLower)) {
                return 80;
            }
        }
        if (pinyin != null && pinyin.contains(termLower)) {
            return 85;
        }
        if (initials != null && initials.contains(termLower)) {
            return 75;
        }
        return 0;
    }

    /** 整串拼音匹配：歌名 → 歌手 → 专辑，命中即止（与旧实现一致）。 */
    private static int legacyPinyinScore(MusicFields fields, String termLower) {
        int title = pinyinFieldScore(fields.titleWordInitials(), fields.titlePinyin(),
                fields.titlePinyinInitials(), termLower);
        if (title > 0) {
            return title;
        }
        int artist = pinyinFieldScore(fields.artistWordInitials(), fields.artistPinyin(),
                fields.artistPinyinInitials(), termLower);
        if (artist > 0) {
            return artist;
        }
        if (fields.albumPinyin() != null && fields.albumPinyin().contains(termLower)) {
            return ALBUM_PINYIN_SCORE;
        }
        return 0;
    }

    private static int bestRoleAssignment(int[] titleScores, int[] artistScores, int[] albumScores) {
        int count = titleScores.length;
        int combinations = 1;
        for (int i = 0; i < count; i++) {
            combinations *= 3;
        }

        int best = 0;
        for (int mask = 0; mask < combinations; mask++) {
            int title = 0;
            int artist = 0;
            int album = 0;
            int rest = mask;
            for (int i = 0; i < count; i++) {
                int role = rest % 3;
                rest /= 3;
                switch (role) {
                    case 0 -> title = Math.max(title, titleScores[i]);
                    case 1 -> artist = Math.max(artist, artistScores[i]);
                    default -> album = Math.max(album, albumScores[i]);
                }
            }
            int score = title * TITLE_ROLE_MULTIPLIER + artist * ARTIST_ROLE_MULTIPLIER + album;
            if (score > best) {
                best = score;
            }
        }
        return best;
    }

    private static List<String> splitOnSeparators(String text) {
        List<String> pieces = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (TERM_SEPARATORS.indexOf(c) >= 0) {
                if (current.length() > 0) {
                    pieces.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            pieces.add(current.toString());
        }
        return pieces;
    }

    /** 纯英文长句（>= 4 个单词、无中日韩字符）按歌词片段处理，不拆分。 */
    private static boolean isFreeTextPhrase(String query) {
        int latinWords = 0;
        int cjkCount = 0;
        boolean inLatinWord = false;
        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if (isCjk(c)) {
                cjkCount++;
                inLatinWord = false;
                continue;
            }
            boolean latinLetter = Character.isLetter(c)
                    && Character.UnicodeScript.of(c) == Character.UnicodeScript.LATIN;
            if (latinLetter) {
                if (!inLatinWord) {
                    latinWords++;
                    inLatinWord = true;
                }
            } else {
                inLatinWord = false;
            }
        }
        return cjkCount == 0 && latinWords >= FREE_TEXT_LATIN_WORD_THRESHOLD;
    }

    private static boolean isCjk(char c) {
        Character.UnicodeScript script = Character.UnicodeScript.of(c);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }
}
