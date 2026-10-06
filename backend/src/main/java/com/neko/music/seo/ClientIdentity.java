package com.neko.music.seo;

import java.util.regex.Pattern;

/**
 * 客户端自报标识的判定。
 *
 * <p>官方三端（Web / PC / Android，见前端 {@code config/clientIdentity.js}）与登记的
 * 第三方客户端统一在 API 请求上带 {@code X-Neko-Client: <端>+<版本>}，例如
 * {@code web+1.2.3} / {@code pc+2026.105.48} / {@code archoera+0.9.20}。</p>
 *
 * <p>防爬过滤器把该头部作为「这是我们的客户端」的正向证据。这一点很关键：桌面 / 移动端
 * 客户端为了兼容各平台 CDN，常常把全局 User-Agent 伪装成普通 Chrome UA，仅凭 UA 结构
 * 无法与爬虫区分；有了显式标识就能精确放行，不再依赖「空 UA / 自定义 UA」这类脆弱假设。</p>
 *
 * <p><b>安全边界说明：</b>该头部可被伪造，因此它只是防爬（善意客户端的兼容开关），
 * 不是鉴权手段。真正的兜底仍是第 1 步关键词黑名单、浏览器特征头校验与 IP 频率限制。</p>
 */
public final class ClientIdentity {

    /** 客户端标识请求头名；三端与文档必须保持一致。 */
    public static final String HEADER = "X-Neko-Client";

    /**
     * {@code <端>+<版本>} 格式：端名以字母开头，只含字母 / 数字 / {@code . _ -}；
     * 版本允许 git describe 结果中的 {@code . _ + -}。
     */
    private static final Pattern CLIENT_VALUE =
            Pattern.compile("^[A-Za-z][A-Za-z0-9._-]{0,31}\\+[A-Za-z0-9._+-]{1,64}$");

    private ClientIdentity() {
    }

    /** 是否为格式合法的 {@link #HEADER} 值（空 / 残缺标识返回 {@code false}）。 */
    public static boolean isDeclaredClient(String headerValue) {
        if (headerValue == null) {
            return false;
        }
        return CLIENT_VALUE.matcher(headerValue.trim()).matches();
    }
}
