package com.neko.music.seo;

/**
 * JSON-LD 内联到 {@code <script>} 时的安全转义。
 *
 * <p>Jackson 默认不会转义 {@code <}、{@code >}、{@code &}。若用户可控字段（歌单名、歌曲名、
 * 描述等）里出现 {@code </script>}，就可能提前闭合脚本标签，造成脚本注入。这里把这三个字符
 * 转成等价的 JSON Unicode 转义，语义不变但无法闭合标签。</p>
 */
final class JsonLd {

    private JsonLd() {
    }

    static String scriptSafe(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        return json.replace("<", "\\u003c")
                .replace(">", "\\u003e")
                .replace("&", "\\u0026");
    }
}
