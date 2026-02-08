package com.neko.music.util;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 拼音转换工具类
 * 支持中文转拼音首字母、完整拼音以及同音字搜索
 */
public class PinyinUtil {
    
    private static final HanyuPinyinOutputFormat format;
    
    static {
        format = new HanyuPinyinOutputFormat();
        format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
    }
    
    /**
     * 获取字符串的拼音首字母
     * @param str 输入字符串
     * @return 拼音首字母字符串
     */
    public static String getPinyinInitials(String str) {
        if (str == null || str.isEmpty()) {
            return "";
        }
        
        StringBuilder initials = new StringBuilder();
        for (char c : str.toCharArray()) {
            String[] pinyinArray = getPinyinArray(c);
            if (pinyinArray != null && pinyinArray.length > 0) {
                initials.append(pinyinArray[0].charAt(0));
            } else {
                initials.append(Character.toLowerCase(c));
            }
        }
        return initials.toString();
    }
    
    /**
     * 获取字符串的完整拼音（小写，无声调）
     * @param str 输入字符串
     * @return 完整拼音字符串，多音字取第一个
     */
    public static String getPinyin(String str) {
        return getPinyin(str, false);
    }
    
    /**
     * 获取字符串的完整拼音
     * @param str 输入字符串
     * @param includeOriginal 是否包含原始字符串
     * @return 完整拼音字符串
     */
    public static String getPinyin(String str, boolean includeOriginal) {
        if (str == null || str.isEmpty()) {
            return includeOriginal ? str : "";
        }
        
        StringBuilder pinyin = new StringBuilder();
        for (char c : str.toCharArray()) {
            String[] pinyinArray = getPinyinArray(c);
            if (pinyinArray != null && pinyinArray.length > 0) {
                pinyin.append(pinyinArray[0]);
            } else {
                pinyin.append(Character.toLowerCase(c));
            }
        }
        return pinyin.toString();
    }
    
    /**
     * 获取字符串的所有可能拼音变体（用于模糊搜索）
     * 包括：原始字符串、拼音首字母、完整拼音
     * @param str 输入字符串
     * @return 所有变体的集合
     */
    public static Set<String> getPinyinVariants(String str) {
        Set<String> variants = new HashSet<>();
        
        if (str == null || str.isEmpty()) {
            variants.add("");
            return variants;
        }
        
        // 原始字符串
        variants.add(str);
        
        // 拼音首字母
        String initials = getPinyinInitials(str);
        if (!initials.isEmpty() && !initials.equals(str.toLowerCase())) {
            variants.add(initials);
        }
        
        // 完整拼音
        String fullPinyin = getPinyin(str);
        if (!fullPinyin.isEmpty() && !fullPinyin.equals(str.toLowerCase())) {
            variants.add(fullPinyin);
        }
        
        // 对于短词（2-4个字符），生成部分拼音匹配
        if (str.length() >= 2 && str.length() <= 4) {
            String[] pinyinParts = str.chars()
                .mapToObj(c -> {
                    String[] arr = getPinyinArray((char) c);
                    return (arr != null && arr.length > 0) ? arr[0] : "";
                })
                .toArray(String[]::new);
            
            // 生成相邻拼音组合（例如：豪大大 -> haoda, daji, jp）
            for (int i = 0; i < pinyinParts.length - 1; i++) {
                String combined = pinyinParts[i] + pinyinParts[i + 1];
                variants.add(combined);
            }
        }
        
        return variants;
    }
    
    /**
     * 判断查询字符串是否可能是拼音
     * @param query 查询字符串
     * @return 如果可能包含拼音返回true
     */
    public static boolean isLikelyPinyin(String query) {
        if (query == null || query.isEmpty()) {
            return false;
        }
        
        // 检查是否包含英文字母（排除中文字符）
        for (char c : query.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 判断查询字符串是否可能是拼音首字母
     * @param query 查询字符串
     * @return 如果可能是拼音首字母返回true
     */
    public static boolean isPinyinInitials(String query) {
        if (query == null || query.isEmpty()) {
            return false;
        }
        
        // 检查是否只包含单个小写字母（不包含拼音的完整拼写）
        return query.matches("^[a-z]+$") && query.length() <= 10;
    }
    
    /**
     * 获取字符的拼音数组
     * @param c 字符
     * @return 拼音数组，非中文字符返回null
     */
    private static String[] getPinyinArray(char c) {
        try {
            return PinyinHelper.toHanyuPinyinStringArray(c, format);
        } catch (BadHanyuPinyinOutputFormatCombination e) {
            return null;
        }
    }
    
    /**
     * 生成用于搜索的拼音模式（SQL LIKE）
     * 例如：输入 "hddjp"，返回 "%hddjp%", "%hao da da ji piao%"
     * @param query 查询字符串
     * @return 拼音搜索模式列表
     */
    public static List<String> getPinyinSearchPatterns(String query) {
        List<String> patterns = new ArrayList<>();
        
        if (query == null || query.isEmpty()) {
            patterns.add("");
            return patterns;
        }
        
        // 添加原始查询
        patterns.add(query);
        
        // 如果是拼音首字母，尝试匹配完整拼音
        if (isPinyinInitials(query)) {
            patterns.add(query + "%");
        }
        
        // 如果是完整拼音，也尝试匹配部分拼音
        if (isLikelyPinyin(query) && query.length() > 2) {
            patterns.add(query + "%");
        }
        
        return patterns;
    }
}