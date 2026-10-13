package com.neko.music.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 访问日志的路径分类：访问量统计按类别聚合，分类错了报表就不可信。 */
class RequestCategoryTest {

    @Test
    @DisplayName("后台路径优先于普通 API")
    void classifiesAdminBeforeApi() {
        assertEquals(RequestCategory.ADMIN, RequestCategory.of("/api/admin/users/12"));
        assertEquals(RequestCategory.ADMIN, RequestCategory.of("/api/admin"));
        assertEquals(RequestCategory.API, RequestCategory.of("/api/adminx"));
        assertEquals(RequestCategory.API, RequestCategory.of("/api/music/search"));
        assertEquals(RequestCategory.API, RequestCategory.of("/api"));
        assertEquals(RequestCategory.API, RequestCategory.of("/loser/x"));
    }

    @Test
    @DisplayName("媒体、静态资源与页面")
    void classifiesStaticAndPage() {
        assertEquals(RequestCategory.MEDIA, RequestCategory.of("/media/music/1.mp3"));
        assertEquals(RequestCategory.MEDIA, RequestCategory.of("/update/setup.exe"));
        assertEquals(RequestCategory.STATIC, RequestCategory.of("/assets/index-abc123.js"));
        assertEquals(RequestCategory.STATIC, RequestCategory.of("/favicon.ico"));
        assertEquals(RequestCategory.PAGE, RequestCategory.of("/detail/abc"));
        assertEquals(RequestCategory.PAGE, RequestCategory.of("/"));
        assertEquals(RequestCategory.PAGE, RequestCategory.of("/download"));
        assertEquals(RequestCategory.OTHER, RequestCategory.of(""));
    }
}
