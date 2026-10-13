package com.neko.music.util;

import jakarta.servlet.ServletRequest;

/**
 * 把「本次响应是不是 SEO 服务端渲染」挂到请求属性上，供访问日志落库。
 *
 * <p>为什么需要单独记：站内多个路由对同一 URL 有**两种表现**——爬虫 / 链接预览拿到服务端渲染的
 * 完整 HTML（含 meta 与 JSON-LD），普通浏览器拿到 SPA 壳（见 {@code MusicDetailPageHandler}、
 * {@code PlaylistDetailPageHandler}、{@code StaticPageSeoFilter}）。日志里的「分类」是按路径算的
 * （{@code /detail/148} 永远是 page），光看它分不出这次到底是「被搜索引擎收录」还是「用户在看页面」，
 * 所以由决定表示形态的那一处把结果挂回请求，访问日志统一落库。</p>
 *
 * <p>该标记只出现在后台日志页，不属于客户端契约，可随实现调整；不要写进对外 API 文档。</p>
 */
public final class SeoAudit {

    /** 请求属性：本次响应是否为服务端 SEO 渲染。 */
    public static final String ATTR_SEO_RENDER = "neko.seo.render";

    private SeoAudit() {
    }

    /** 标记本次响应是服务端 SEO 渲染（爬虫 / 链接预览拿到的那一种表示）。 */
    public static void mark(ServletRequest request) {
        set(request, Boolean.TRUE);
    }

    /**
     * 标记本次响应走了另一条分支（浏览器拿到的 SPA 壳）。
     *
     * <p>要显式记 false 而不是留空：留空会被读成「与 SEO 无关」，于是「爬虫收录了一个详情页」和
     * 「用户自己打开同一个详情页」在日志里就分不出来了。</p>
     */
    public static void markNotSeo(ServletRequest request) {
        set(request, Boolean.FALSE);
    }

    /** 读取本次是否走了 SEO 渲染分支；未记录时为 null（与 SEO 无关的路径）。 */
    public static Boolean seoRender(ServletRequest request) {
        if (request == null) {
            return null;
        }
        return request.getAttribute(ATTR_SEO_RENDER) instanceof Boolean value ? value : null;
    }

    private static void set(ServletRequest request, Boolean value) {
        if (request != null) {
            request.setAttribute(ATTR_SEO_RENDER, value);
        }
    }
}
