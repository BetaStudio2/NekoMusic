package com.neko.music.model;

/**
 * 统一的成功响应体：{@code {"success":true,"message":"..."}}。
 *
 * <p>此前 5 个处理器各自复制了一份字段与方法完全相同的私有内部类
 * {@code SuccessResponse}。统一为 record 后这些副本全部删除，
 * 序列化字段名与结构保持不变。
 */
public record SuccessResponse(boolean success, String message) {
}
