package com.neko.music.util;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 从歌曲元数据文本推断语种（网易云 API 不提供 language 字段）。
 * 返回值与前台/上传白名单一致：中文、粤语、上海语、英文、日语、韩语、法语、德语、俄语、纯音乐。
 */
public final class SongLanguageInferer {

    private static final Pattern INSTRUMENTAL_HINT = Pattern.compile(
            "(?i)(instrumental|inst\\.?|off\\s*vocal|acoustic\\s*version|伴奏|纯音乐|器乐|钢琴曲|交响曲|symphony|sonata|no\\s*vocal)");
    private static final Pattern CANTONESE_HINT = Pattern.compile(
            "(粤语|广东话|cantonese|hmong)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHANGHAINESE_HINT = Pattern.compile(
            "(上海话|沪语|shanghainese)", Pattern.CASE_INSENSITIVE);

    private SongLanguageInferer() {
    }

    public static String infer(String title, String artist, String album, String lrcSample) {
        String combined = joinNonBlank(title, artist, album);
        String lrc = lrcSample == null ? "" : lrcSample;

        if (isInstrumental(combined, lrc)) {
            return "纯音乐";
        }
        if (CANTONESE_HINT.matcher(combined).find()) {
            return "粤语";
        }
        if (SHANGHAINESE_HINT.matcher(combined).find()) {
            return "上海语";
        }

        ScriptStats stats = analyzeScripts(combined + "\n" + stripLrcMeta(lrc));
        if (stats.totalLetters == 0) {
            return "中文";
        }

        double hangul = ratio(stats.hangul, stats.totalLetters);
        double kana = ratio(stats.kana, stats.totalLetters);
        double cyrillic = ratio(stats.cyrillic, stats.totalLetters);
        double cjk = ratio(stats.cjk, stats.totalLetters);
        double latin = ratio(stats.latin, stats.totalLetters);

        if (hangul >= 0.25) {
            return "韩语";
        }
        if (kana >= 0.12) {
            return "日语";
        }
        if (cyrillic >= 0.25) {
            return "俄语";
        }
        if (cjk >= 0.2) {
            return "中文";
        }
        if (latin >= 0.35) {
            if (looksFrench(combined)) {
                return "法语";
            }
            if (looksGerman(combined)) {
                return "德语";
            }
            return "英文";
        }
        if (cjk > latin) {
            return "中文";
        }
        if (latin > 0.1) {
            return "英文";
        }
        return "中文";
    }

    private static boolean isInstrumental(String meta, String lrc) {
        if (INSTRUMENTAL_HINT.matcher(meta).find()) {
            return true;
        }
        String body = stripLrcMeta(lrc);
        if (body.isBlank()) {
            return false;
        }
        if (body.contains("暂无歌词") || body.contains("Neko云音乐")) {
            return INSTRUMENTAL_HINT.matcher(meta).find();
        }
        long lyricLines = body.lines().filter(SongLanguageInferer::looksLikeLyricLine).count();
        return lyricLines <= 2 && INSTRUMENTAL_HINT.matcher(meta).find();
    }

    private static boolean looksLikeLyricLine(String line) {
        String t = line.trim();
        return !t.isEmpty() && t.startsWith("[") && t.contains("]");
    }

    private static String stripLrcMeta(String lrc) {
        if (lrc == null || lrc.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String line : lrc.split("\n")) {
            String t = line.trim();
            if (t.startsWith("[ti:") || t.startsWith("[ar:") || t.startsWith("[al:")
                    || t.startsWith("[by:") || t.startsWith("[offset:")) {
                continue;
            }
            if (t.startsWith("[") && t.contains("]") && t.length() < 32
                    && t.matches("\\[\\d{2}:\\d{2}.*")) {
                continue;
            }
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static ScriptStats analyzeScripts(String text) {
        ScriptStats s = new ScriptStats();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (Character.isLetter(cp)) {
                s.totalLetters++;
                if (isHangul(cp)) {
                    s.hangul++;
                } else if (isKana(cp)) {
                    s.kana++;
                } else if (isCyrillic(cp)) {
                    s.cyrillic++;
                } else if (isCjk(cp)) {
                    s.cjk++;
                } else if (isLatin(cp)) {
                    s.latin++;
                }
            }
            i += Character.charCount(cp);
        }
        return s;
    }

    private static boolean looksFrench(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return text.indexOf('é') >= 0 || text.indexOf('è') >= 0 || text.indexOf('ê') >= 0
                || text.indexOf('à') >= 0 || text.indexOf('ç') >= 0
                || lower.contains(" le ") || lower.contains(" de ") || lower.contains(" les ");
    }

    private static boolean looksGerman(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return text.indexOf('ä') >= 0 || text.indexOf('ö') >= 0 || text.indexOf('ü') >= 0
                || text.indexOf('ß') >= 0
                || lower.contains(" und ") || lower.contains(" der ") || lower.contains(" die ");
    }

    private static double ratio(int part, int total) {
        return total == 0 ? 0 : (double) part / total;
    }

    private static String joinNonBlank(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(p.trim());
            }
        }
        return sb.toString();
    }

    private static boolean isHangul(int cp) {
        return cp >= 0xAC00 && cp <= 0xD7AF || cp >= 0x1100 && cp <= 0x11FF;
    }

    private static boolean isKana(int cp) {
        return cp >= 0x3040 && cp <= 0x30FF || cp >= 0x31F0 && cp <= 0x31FF;
    }

    private static boolean isCyrillic(int cp) {
        return cp >= 0x0400 && cp <= 0x04FF;
    }

    private static boolean isCjk(int cp) {
        Character.UnicodeScript script = Character.UnicodeScript.of(cp);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.BOPOMOFO;
    }

    private static boolean isLatin(int cp) {
        return Character.UnicodeScript.of(cp) == Character.UnicodeScript.LATIN;
    }

    private static final class ScriptStats {
        int totalLetters;
        int hangul;
        int kana;
        int cyrillic;
        int cjk;
        int latin;
    }
}
