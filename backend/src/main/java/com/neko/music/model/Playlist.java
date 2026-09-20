package com.neko.music.model;

/**
 * 歌单。只读数据载体（JDBC 行映射后即序列化，不再修改）。
 *
 * <p>字段名与顺序与旧 POJO 完全一致；保留 {@code (userId, name, description)}
 * 便捷构造器，供创建歌单时定位新行。序列化由各处手动构造的 JsonObject 完成，
 * 不依赖反射，故 JSON 结构不变。
 */
public record Playlist(
        int id,
        int userId,
        String name,
        String description,
        int musicCount,
        String createdAt,
        String updatedAt) {

    public Playlist(int userId, String name, String description) {
        this(0, userId, name, description, 0, null, null);
    }
}
