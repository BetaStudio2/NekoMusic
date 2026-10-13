package com.neko.music.service;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 日志读写的小工具：字段裁剪、LIKE 转义、结果集转 Map。
 *
 * <p>只服务于访问日志 / 操作日志两张表，不应被业务代码复用。</p>
 */
final class LogQuerySupport {

    private LogQuerySupport() {
    }

    /** 按列宽裁剪：截断比插入失败更好——一条超长字段会让整批日志写入失败。 */
    static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    /** 含通配的 LIKE 模式：转义 {@code \ % _}，两侧加 {@code %}。 */
    static String likePattern(String keyword) {
        StringBuilder builder = new StringBuilder(keyword.length() + 2);
        builder.append('%');
        for (int i = 0; i < keyword.length(); i++) {
            char c = keyword.charAt(i);
            if (c == '\\' || c == '%' || c == '_') {
                builder.append('\\');
            }
            builder.append(c);
        }
        builder.append('%');
        return builder.toString();
    }

    /** 结果集 → 行 Map（列名保持数据库原样，便于前端直接按列名取值）。 */
    static List<Map<String, Object>> toMaps(ResultSet rs) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        ResultSetMetaData meta = rs.getMetaData();
        int columns = meta.getColumnCount();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columns; i++) {
                Object value = rs.getObject(i);
                if (value instanceof java.sql.Timestamp timestamp) {
                    row.put(meta.getColumnLabel(i), timestamp.getTime());
                } else {
                    row.put(meta.getColumnLabel(i), value);
                }
            }
            rows.add(row);
        }
        return rows;
    }

    /** 两列结果集 → {@code key → count}；key 为 null 时归一为空串。 */
    static Map<String, Long> countBy(ResultSet rs, String keyColumn, String countColumn) throws SQLException {
        Map<String, Long> result = new HashMap<>();
        while (rs.next()) {
            String key = rs.getString(keyColumn);
            result.put(key == null ? "" : key, rs.getLong(countColumn));
        }
        return result;
    }
}
