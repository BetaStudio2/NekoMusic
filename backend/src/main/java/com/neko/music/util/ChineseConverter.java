package com.neko.music.util;

import com.github.houbb.opencc4j.util.ZhConverterUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 繁简体转换工具类
 * 使用 OpenCC 库进行繁简体转换
 */
public class ChineseConverter {
    
    /**
     * 将简体中文转换为繁体中文
     * @param text 输入文本
     * @return 转换后的繁体中文
     */
    public static String toTraditional(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return ZhConverterUtil.toTraditional(text);
    }
    
    /**
     * 将繁体中文转换为简体中文
     * @param text 输入文本
     * @return 转换后的简体中文
     */
    public static String toSimplified(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return ZhConverterUtil.toSimple(text);
    }
    
    /**
     * 生成搜索用的繁简体版本
     * @param query 搜索关键词
     * @return 包含简体和繁体的数组
     */
    public static String[] getSearchVariants(String query) {
        if (query == null || query.isEmpty()) {
            return new String[]{query};
        }
        
        String simplified = toSimplified(query);
        String traditional = toTraditional(query);
        
        // 如果转换后与原文本相同，只返回一个版本
        if (simplified.equals(traditional)) {
            return new String[]{simplified};
        }
        
        // 返回简体和繁体两个版本
        return new String[]{simplified, traditional};
    }
    
    /**
     * 生成完整的搜索变体（包含繁简体和拼音）
     * 支持以下搜索方式：
     * 1. 原始文本（简体/繁体）
     * 2. 拼音首字母（如 "hddjp" 搜索 "豪大大鸡排"）
     * 3. 完整拼音（如 "haodadajipai" 搜索 "豪大大鸡排"）
     * 4. 同音字/错别字（通过模糊匹配）
     * 
     * @param query 搜索关键词
     * @return 包含所有搜索变体的列表
     */
    public static List<String> getFullSearchVariants(String query) {
        List<String> variants = new ArrayList<>();
        Set<String> uniqueVariants = new HashSet<>();
        
        if (query == null || query.isEmpty()) {
            variants.add("");
            return variants;
        }
        
        // 1. 添加原始查询
        uniqueVariants.add(query);
        
        // 2. 添加繁简体变体
        String simplified = toSimplified(query);
        String traditional = toTraditional(query);
        uniqueVariants.add(simplified);
        uniqueVariants.add(traditional);
        
        // 3. 如果查询是拼音（包含字母），则尝试匹配中文
        if (PinyinUtil.isLikelyPinyin(query)) {
            // 如果是拼音首字母（如 "hddjp"），添加以这些字母开头的完整拼音模式
            if (PinyinUtil.isPinyinInitials(query)) {
                uniqueVariants.add(query);
                // 对于拼音首字母搜索，我们将在SQL中使用模糊匹配
            }
            // 如果是完整拼音（如 "haodadajipai"），也添加
            else {
                uniqueVariants.add(query);
            }
        }
        
        // 4. 如果查询包含中文，生成拼音变体用于反向匹配
        if (containsChinese(query)) {
            // 获取拼音首字母
            String initials = PinyinUtil.getPinyinInitials(query);
            if (!initials.isEmpty()) {
                uniqueVariants.add(initials);
            }
            
            // 获取完整拼音
            String fullPinyin = PinyinUtil.getPinyin(query);
            if (!fullPinyin.isEmpty()) {
                uniqueVariants.add(fullPinyin);
            }
            
            // 获取所有拼音变体（包括部分组合）
            Set<String> pinyinVariants = PinyinUtil.getPinyinVariants(query);
            uniqueVariants.addAll(pinyinVariants);
        }
        
        // 转换为列表并去重
        variants.addAll(uniqueVariants);
        
        return variants;
    }
    
    /**
     * 判断字符串是否包含中文字符
     * @param str 输入字符串
     * @return 如果包含中文字符返回true
     */
    private static boolean containsChinese(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        
        for (char c : str.toCharArray()) {
            if (isChinese(c)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 判断字符是否是中文字符
     * @param c 字符
     * @return 如果是中文字符返回true
     */
    private static boolean isChinese(char c) {
        return (c >= 0x4E00 && c <= 0x9FA5) || 
               (c >= 0x3400 && c <= 0x4DBF) || 
               (c >= 0x20000 && c <= 0x2A6DF) || 
               (c >= 0x2A700 && c <= 0x2B73F) || 
               (c >= 0x2B740 && c <= 0x2B81F) || 
               (c >= 0x2B820 && c <= 0x2CEAF) || 
               (c >= 0xF900 && c <= 0xFAFF) || 
               (c >= 0x2F800 && c <= 0x2FA1F);
    }
}