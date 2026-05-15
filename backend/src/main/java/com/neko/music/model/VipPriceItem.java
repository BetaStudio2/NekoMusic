package com.neko.music.model;

/**
 * VIP 价目表一行：时长（月 + 天）与价格（元）。
 */
public class VipPriceItem {
    private int id;
    private int months;
    private int days;
    private double priceYuan;
    private int sortOrder;
    private String updatedAt;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getMonths() {
        return months;
    }

    public void setMonths(int months) {
        this.months = months;
    }

    public int getDays() {
        return days;
    }

    public void setDays(int days) {
        this.days = days;
    }

    public double getPriceYuan() {
        return priceYuan;
    }

    public void setPriceYuan(double priceYuan) {
        this.priceYuan = priceYuan;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
