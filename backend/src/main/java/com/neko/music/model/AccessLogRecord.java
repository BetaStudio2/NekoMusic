package com.neko.music.model;

/**
 * 一条访问日志。
 *
 * <p>面向安全分析，「数据要全」：不做采样、不截断到看不清客户端身份，UA 原样保留
 * （仅在超长时按列宽上限裁剪，避免插入失败）。不记录 {@code Authorization} 令牌原文。</p>
 *
 * @param occurredAt     请求时间（epoch 毫秒）
 * @param method         HTTP 方法
 * @param path           路由路径（不含查询串）
 * @param query          原始查询串，无则为 null
 * @param category       路径分类：api/static/media/page/admin/other
 * @param status         响应状态码
 * @param durationUs     处理耗时（微秒）
 * @param responseBytes  响应体字节数（取 Content-Length，未知为 0）
 * @param clientIp       可信客户端 IP
 * @param ipSubnet       同网段聚合键（IPv4 /24、IPv6 /48），无 IP 时为 null
 * @param userAgent      原始 User-Agent
 * @param clientType     客户端类型：browser/official_client/third_party/bot/scanner/unknown
 * @param clientPlatform 解析出的平台（web/android/pc/…）
 * @param clientVersion  解析出的客户端版本
 * @param nekoClient     {@code X-Neko-Client} 头原值
 * @param clientId       {@code X-Neko-Client-Id} 头原值（应用公开标识）
 * @param clientInstanceId {@code X-Neko-Client-Instance} 头原值（安装实例 ID）
 * @param referer        Referer
 * @param origin         Origin
 * @param acceptLanguage Accept-Language
 * @param authenticated  是否携带令牌：{@code Authorization} 头（裸 token 或 {@code Bearer} 前缀）或
 *                       {@code ?token=} 任一形态。只说明「带到令牌了」，令牌能不能换成身份看
 *                       {@code userId} / {@code adminId}
 * @param userId         已登录用户 ID（用户令牌有效时解析，否则为 null）
 * @param userName       已登录用户昵称（与 {@code userId} 同时解析，随日志快照保存）
 * @param adminId        管理员 ID（仅管理端路径解析，其它为 null）
 * @param adminUsername  管理员用户名（同上）
 * @param host           Host 头
 * @param scheme         请求 scheme
 * @param protocol       协议（HTTP/1.1 等）
 * @param thread         处理线程名
 * @param powDifficulty  本次请求对应的 PoW 难度（比特）；不涉及 PoW 或未知时为 null
 * @param powStatus      PoW 结果标签（verified / missing / issued / rate_limited…），见
 *                       {@link com.neko.music.util.PowAudit}；不涉及 PoW 时为 null
 * @param seoRender      本次是否返回了服务端 SEO 渲染的 HTML（爬虫 / 链接预览拿到的表示）；
 *                       非 SEO 分支（浏览器拿到 SPA 壳）为 false，与 SEO 无关的路径为 null
 * @param threatLevel    威胁判定：normal / suspected / attack
 * @param threatScore    综合风险分（0-100）
 * @param threatReasons  命中原因摘要（内部运维词表，不对客户端公开）
 * @param threatEngineVersion 判定引擎版本；旧记录回填后与当前版本对齐
 */
public record AccessLogRecord(
        long occurredAt,
        String method,
        String path,
        String query,
        String category,
        int status,
        long durationUs,
        long responseBytes,
        String clientIp,
        String ipSubnet,
        String userAgent,
        String clientType,
        String clientPlatform,
        String clientVersion,
        String nekoClient,
        String clientId,
        String clientInstanceId,
        String referer,
        String origin,
        String acceptLanguage,
        boolean authenticated,
        Integer userId,
        String userName,
        Integer adminId,
        String adminUsername,
        String host,
        String scheme,
        String protocol,
        String thread,
        Integer powDifficulty,
        String powStatus,
        Boolean seoRender,
        String threatLevel,
        int threatScore,
        String threatReasons,
        int threatEngineVersion) {
}
