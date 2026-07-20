package com.neko.music.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 LRC 歌词为带时间戳的行列表，供视频渲染滚动字幕使用。
 */
public final class LrcParser {
    /** [mm:ss.xx] 与 [mm:ss:xx]（部分 LRC 使用冒号分隔毫秒） */
    private static final Pattern TIME_STAMP = Pattern.compile("\\[(\\d{2}):(\\d{2})[:.](\\d{1,5})\\]");
    private static final Pattern OFFSET = Pattern.compile("\\[offset:\\s*([+-]?\\d+)\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRANSLATION = Pattern.compile("^\\{[\"'](.+)[\"']\\}$");

    private LrcParser() {
    }

    public static final class Line {
        private final double timeSec;
        private final String text;
        private final String translation;

        public Line(double timeSec, String text, String translation) {
            this.timeSec = timeSec;
            this.text = text == null ? "" : text.trim();
            this.translation = translation == null ? "" : translation.trim();
        }

        public double getTimeSec() {
            return timeSec;
        }

        public String getText() {
            return text;
        }

        public String getTranslation() {
            return translation;
        }

        public boolean hasTranslation() {
            return !translation.isEmpty();
        }
    }

    public static Optional<List<Line>> parseFile(Path lrcFile) {
        if (lrcFile == null || !Files.isRegularFile(lrcFile)) {
            return Optional.empty();
        }
        try (BufferedReader reader = Files.newBufferedReader(lrcFile, StandardCharsets.UTF_8)) {
            List<String> rawLines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                rawLines.add(line);
            }
            List<Line> parsed = parseLines(rawLines);
            return parsed.isEmpty() ? Optional.empty() : Optional.of(parsed);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public static Optional<List<Line>> parseString(String content) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }
        List<String> rawLines = content.lines().toList();
        List<Line> parsed = parseLines(rawLines);
        return parsed.isEmpty() ? Optional.empty() : Optional.of(parsed);
    }

    static List<Line> parseLines(List<String> rawLines) {
        List<Line> result = new ArrayList<>();
        double offsetSec = 0;
        for (int i = 0; i < rawLines.size(); i++) {
            String line = rawLines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            Matcher offsetMatcher = OFFSET.matcher(line);
            if (offsetMatcher.find()) {
                offsetSec = Integer.parseInt(offsetMatcher.group(1)) / 1000.0;
                continue;
            }
            Matcher matcher = TIME_STAMP.matcher(line);
            if (!matcher.find()) {
                continue;
            }
            double timeSec = parseTimestamp(matcher) + offsetSec;
            if (timeSec < 0) {
                timeSec = 0;
            }
            String text = line.substring(matcher.end()).trim();
            if (text.isEmpty()) {
                continue;
            }
            String translation = "";
            if (i + 1 < rawLines.size()) {
                String next = rawLines.get(i + 1).trim();
                Matcher tr = TRANSLATION.matcher(next);
                if (tr.matches()) {
                    translation = tr.group(1);
                }
            }
            result.add(new Line(timeSec, text, translation));
        }
        result.sort((a, b) -> Double.compare(a.timeSec, b.timeSec));
        return Collections.unmodifiableList(deduplicateByTime(result));
    }

    /** 同一时刻保留首条，避免滚动索引错乱 */
    private static List<Line> deduplicateByTime(List<Line> sorted) {
        if (sorted.isEmpty()) {
            return sorted;
        }
        List<Line> out = new ArrayList<>();
        Line prev = null;
        for (Line line : sorted) {
            if (prev != null && Math.abs(line.timeSec - prev.timeSec) < 1e-4) {
                continue;
            }
            out.add(line);
            prev = line;
        }
        return out;
    }

    private static double parseTimestamp(Matcher matcher) {
        int minutes = Integer.parseInt(matcher.group(1));
        int seconds = Integer.parseInt(matcher.group(2));
        String msRaw = matcher.group(3);
        int msDigits = msRaw.length();
        double divisor = switch (msDigits) {
            case 1 -> 10.0;
            case 2 -> 100.0;
            case 3 -> 1000.0;
            case 4 -> 10000.0;
            default -> 100000.0;
        };
        double fractional = Integer.parseInt(msRaw) / divisor;
        return minutes * 60.0 + seconds + fractional;
    }
}
