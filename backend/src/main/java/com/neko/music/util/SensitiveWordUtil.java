package com.neko.music.util;

import com.github.houbb.sensitive.word.core.SensitiveWordHelper;
import java.util.List;

/**
 * 违禁词检测工具类，封装 sensitive-word 库
 */
public class SensitiveWordUtil {

    /**
     * 检测文本是否包含违禁词
     * 检测前会去除空白字符，防止通过空格等方式绕过
     */
    public static boolean contains(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String normalized = removeWhitespace(text);
        if (normalized.isEmpty()) {
            return false;
        }
        return SensitiveWordHelper.contains(normalized);
    }

    /**
     * 查找文本中的所有违禁词
     * 检测前会去除空白字符，防止通过空格等方式绕过
     */
    public static List<String> findAll(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        String normalized = removeWhitespace(text);
        if (normalized.isEmpty()) {
            return List.of();
        }
        return SensitiveWordHelper.findAll(normalized);
    }

    /**
     * 去除文本中的空白字符和零宽字符，防止绕过检测
     */
    private static String removeWhitespace(String text) {
        return text.replaceAll("[\\s\\u200B\\u200C\\u200D\\u2060\\uFEFF]+", "");
    }
}
