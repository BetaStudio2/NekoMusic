package com.neko.music.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LRC歌词文件校验工具类
 * 用于校验上传的歌词文件格式是否符合要求
 */
public class LrcValidator {

    // 最大文件大小：200KB
    private static final long MAX_FILE_SIZE = 200 * 1024;

    // 时间戳正则表达式：[mm:ss.xx] 或 [mm:ss.xxx]
    private static final Pattern TIME_STAMP_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\]");

    // 翻译行正则表达式：{"翻译内容"}
    private static final Pattern TRANSLATION_PATTERN = Pattern.compile("^\\{\"\'(.+)[\"\']\\}$");

    /**
     * 校验LRC文件
     *
     * @param inputStream 文件输入流
     * @param fileSize 文件大小（字节）
     * @return 校验结果，包含是否通过和错误信息
     */
    public static ValidationResult validate(InputStream inputStream, long fileSize) {
        // 1. 校验文件大小
        if (fileSize > MAX_FILE_SIZE) {
            return ValidationResult.fail("歌词文件大小不能超过200KiB");
        }

        // 2. 读取文件内容
        List<String> lines;
        try {
            lines = readLines(inputStream);
        } catch (IOException e) {
            return ValidationResult.fail("歌词文件读取失败：" + e.getMessage());
        }

        // 3. 校验歌词格式
        return validateLyricsFormat(lines);
    }

    /**
     * 读取文件内容到列表
     */
    private static List<String> readLines(InputStream inputStream) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    /**
     * 校验歌词格式
     */
    private static ValidationResult validateLyricsFormat(List<String> lines) {
        if (lines.isEmpty()) {
            return ValidationResult.fail("歌词文件内容为空");
        }

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            // 跳过空行
            if (line.isEmpty()) {
                continue;
            }

            // 检查这一行是否包含时间戳
            List<Integer> timestampPositions = new ArrayList<>();
            Matcher matcher = TIME_STAMP_PATTERN.matcher(line);
            while (matcher.find()) {
                timestampPositions.add(matcher.start());
            }

            // 如果这一行包含时间戳
            if (!timestampPositions.isEmpty()) {
                // 检查是否只有一个时间戳
                if (timestampPositions.size() > 1) {
                    return ValidationResult.fail(
                            String.format("第%d行包含多个时间戳，一行只允许有一个时间戳", i + 1)
                    );
                }

                // 验证时间戳的具体格式
                // 重新匹配以获取完整的时间戳
                Matcher timestampMatcher = TIME_STAMP_PATTERN.matcher(line);
                if (timestampMatcher.find()) {
                    String timestamp = timestampMatcher.group();

                    // 验证时间戳中的数字是否有效
                    String timestampContent = timestamp.substring(1, timestamp.length() - 1); // 去掉 []
                    String[] parts = timestampContent.split(":");
                    if (parts.length != 2) {
                        return ValidationResult.fail(
                                String.format("第%d行时间戳格式错误，格式应为[mm:ss.xx]或[mm:ss.xxx]", i + 1)
                        );
                    }

                    try {
                        String[] timeParts = parts[1].split("\\.");
                        if (timeParts.length != 2) {
                            return ValidationResult.fail(
                                    String.format("第%d行时间戳格式错误，格式应为[mm:ss.xx]或[mm:ss.xxx]", i + 1)
                            );
                        }

                        int minutes = Integer.parseInt(parts[0]);
                        int seconds = Integer.parseInt(timeParts[0]);
                        int milliseconds = Integer.parseInt(timeParts[1]);

                        // 验证数值范围
                        if (minutes < 0 || minutes > 59) {
                            return ValidationResult.fail(
                                    String.format("第%d行时间戳的分钟数无效（0-59）", i + 1)
                            );
                        }
                        if (seconds < 0 || seconds > 59) {
                            return ValidationResult.fail(
                                    String.format("第%d行时间戳的秒数无效（0-59）", i + 1)
                            );
                        }
                        // 毫秒数根据位数判断范围
                        int maxMilliseconds = timeParts[1].length() == 2 ? 99 : 999;
                        if (milliseconds < 0 || milliseconds > maxMilliseconds) {
                            return ValidationResult.fail(
                                    String.format("第%d行时间戳的毫秒数无效（0-%d）", i + 1, maxMilliseconds)
                            );
                        }
                    } catch (NumberFormatException e) {
                        return ValidationResult.fail(
                                String.format("第%d行时间戳包含非数字字符", i + 1)
                        );
                    }
                }

                // 如果下一行是翻译，验证翻译格式
                if (i + 1 < lines.size()) {
                    String nextLine = lines.get(i + 1).trim();
                    // 检查是否是JSON格式的翻译行
                    Matcher translationMatcher = TRANSLATION_PATTERN.matcher(nextLine);
                    if (translationMatcher.find()) {
                        // 这一行看起来是翻译，检查格式是否正确
                        String translationContent = translationMatcher.group(1);
                        // 翻译内容不能为空
                        if (translationContent.isEmpty()) {
                            return ValidationResult.fail(
                                    String.format("第%d行翻译内容不能为空", i + 2)
                            );
                        }
                    }
                }
            }
        }

        return ValidationResult.success();
    }

    /**
     * 校验结果类
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult fail(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
