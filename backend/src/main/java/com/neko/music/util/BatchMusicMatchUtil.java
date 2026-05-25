package com.neko.music.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 批量搜索 / 网易云补全共用的歌名、歌手归一化与匹配。
 */
public final class BatchMusicMatchUtil {

    private static final Pattern PAREN_BLOCK = Pattern.compile(
            "[\\(（\\[【][^\\)）\\]】]*[\\)）\\]】]");
    private static final Pattern ARTIST_SPLIT = Pattern.compile(
            "[/／|,、&＆+＋]|\\s+(?:feat\\.?|ft\\.?|featuring|with|w/)\\s+",
            Pattern.CASE_INSENSITIVE);

    private BatchMusicMatchUtil() {
    }

    /** 去掉括号内备注（feat / remix / ver 等），用于搜索与打分。 */
    public static String coreTitle(String title) {
        if (title == null || title.isBlank()) {
            return "";
        }
        String s = PAREN_BLOCK.matcher(title.trim()).replaceAll(" ").replaceAll("\\s+", " ").trim();
        return s.isEmpty() ? title.trim() : s;
    }

    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return ChineseConverter.toSimplified(s.trim()).toLowerCase(Locale.ROOT);
    }

    public static List<String> artistTokens(String artist) {
        Set<String> tokens = new LinkedHashSet<>();
        if (artist == null || artist.isBlank()) {
            return List.of();
        }
        String norm = normalize(artist);
        if (!norm.isEmpty()) {
            tokens.add(norm);
        }
        for (String part : ARTIST_SPLIT.split(artist)) {
            String t = normalize(part);
            if (!t.isEmpty()) {
                tokens.add(t);
            }
        }
        return new ArrayList<>(tokens);
    }

    /**
     * 请求歌手与曲库歌手是否有关联（任一分段互相包含或相等）。
     */
    public static boolean artistsRelate(String reqArtist, String rowArtist) {
        if (reqArtist == null || reqArtist.isBlank()) {
            return true;
        }
        if (rowArtist == null || rowArtist.isBlank()) {
            return false;
        }
        String rowNorm = normalize(rowArtist);
        for (String token : artistTokens(reqArtist)) {
            if (token.isEmpty()) {
                continue;
            }
            if (rowNorm.equals(token) || rowNorm.contains(token) || token.contains(rowNorm)) {
                return true;
            }
            for (String rowToken : artistTokens(rowArtist)) {
                if (rowToken.equals(token) || rowToken.contains(token) || token.contains(rowToken)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static int scoreArtistContribution(String reqArtist, String rowArtist) {
        if (reqArtist == null || reqArtist.isBlank()) {
            return 0;
        }
        if (rowArtist == null || rowArtist.isBlank()) {
            return -40;
        }
        String rowNorm = normalize(rowArtist);
        String reqNorm = normalize(reqArtist);
        if (rowNorm.equals(reqNorm)) {
            return 50;
        }
        if (artistsRelate(reqArtist, rowArtist)) {
            return 32;
        }
        return -50;
    }

    public static int scoreTitleContribution(String reqTitle, String rowTitle) {
        if (reqTitle == null || reqTitle.isBlank() || rowTitle == null || rowTitle.isBlank()) {
            return 0;
        }
        String reqCore = normalize(coreTitle(reqTitle));
        String rowCore = normalize(coreTitle(rowTitle));
        String reqFull = normalize(reqTitle);
        String rowFull = normalize(rowTitle);
        if (reqCore.isEmpty() || rowCore.isEmpty()) {
            return 0;
        }
        if (rowCore.equals(reqCore) || rowFull.equals(reqFull)) {
            return 100;
        }
        if (rowCore.contains(reqCore) || reqCore.contains(rowCore)
                || rowFull.contains(reqFull) || reqFull.contains(rowFull)) {
            return 72;
        }
        return 0;
    }
}
