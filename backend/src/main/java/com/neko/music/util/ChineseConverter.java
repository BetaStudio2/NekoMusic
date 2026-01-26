package com.neko.music.util;

import com.github.houbb.opencc4j.util.ZhConverterUtil;

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
}