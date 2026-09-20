package com.neko.music.model;

/**
 * 统一的「error」错误响应体：{@code {"error":"..."}}。
 *
 * <p>此前 11 个处理器各自复制了一份内容相同的私有内部类 {@code ErrorResponse}
 * （为 Jackson 序列化而生）。统一为 record 后，这些副本全部删除，
 * 序列化结果仍是 {@code {"error": "<message>"}}，对前端接口零影响。
 */
public record ErrorResponse(String error) {
}
