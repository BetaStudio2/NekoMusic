package com.neko.music.model;

import java.util.List;
import java.util.Map;

/**
 * 日志分页查询结果。
 *
 * @param total 命中总数
 * @param page  页码（从 1 开始）
 * @param size  每页条数
 * @param items 当前页记录（字段名与表列名一致）
 */
public record LogPage(long total, int page, int size, List<Map<String, Object>> items) {
}
