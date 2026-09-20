package com.neko.music.model;

/**
 * VIP 价目表一行：时长（月 + 天）与价格（元）。
 *
 * <p>只读数据载体：由 {@code VipPricingDatabaseManager} 映射后即序列化/读取。
 * 字段名与顺序与旧 POJO 一致。
 */
public record VipPriceItem(
        int id,
        int months,
        int days,
        double priceYuan,
        int sortOrder,
        String updatedAt) {
}
