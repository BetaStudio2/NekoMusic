package com.neko.music.util;

import jakarta.servlet.ServletRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * 把「这次写操作到底改了什么」挂到请求属性上，供操作日志落库（见 {@code RequestAuditFilter}）。
 *
 * <p>为什么不交给过滤器自己推断：变更明细只有业务处理器知道——它读得到请求体，也读得到库里的现值
 * （例如「系统设置」的原值 → 新值）。过滤器在请求前后只看得到路径与状态码，推不出「哪个字段从什么
 * 改成了什么」。于是约定：处理器把明细挂到请求属性，过滤器统一拼成 {@code operation_log.detail}。</p>
 *
 * <p>明细是**给运维看的人话**，只落日志库、只出现在后台日志页，不属于客户端契约，不要写进对外
 * API 文档；敏感项由处理器决定怎么脱敏（本项目只写「已配置 / 已更新 / 已清空」这类状态，不落明文）。</p>
 */
public final class OperationAudit {

    /** 请求属性：本次操作的变更明细（{@link Change} 列表）。 */
    public static final String ATTR_CHANGES = "neko.operation.changes";

    /** 单条变更：改了哪个字段、原来是什么、现在是什么。 */
    public record Change(String label, String before, String after) {
    }

    /** 单值展示上限：超长取值（如整段 UA 白名单）截断，避免一条明细吃掉整列。 */
    private static final int MAX_VALUE_LENGTH = 120;

    /** 明细总长上限，比 {@code operation_log.detail} 列宽（1024）留出余量。 */
    private static final int MAX_DETAIL_LENGTH = 1000;

    /** 空取值的展示形态，避免出现「： → 」这种读不懂的行。 */
    static final String EMPTY_VALUE = "（空）";

    private OperationAudit() {
    }

    /** 追加一条变更明细；请求对象缺失或标签为空时静默忽略。 */
    public static void add(ServletRequest request, String label, String before, String after) {
        if (request == null || label == null || label.isBlank()) {
            return;
        }
        changesMutable(request).add(new Change(label.trim(), before, after));
    }

    /** 读取本次操作累积的变更明细；没有时返回空列表。 */
    public static List<Change> changes(ServletRequest request) {
        if (request == null || !(request.getAttribute(ATTR_CHANGES) instanceof List<?> stored)) {
            return List.of();
        }
        List<Change> result = new ArrayList<>(stored.size());
        for (Object item : stored) {
            if (item instanceof Change change) {
                result.add(change);
            }
        }
        return result;
    }

    /** 读取并拼成 {@code operation_log.detail} 文本；没有明细时返回 {@code null}。 */
    public static String describe(ServletRequest request) {
        return describe(changes(request));
    }

    /**
     * 把变更明细拼成 {@code operation_log.detail} 文本：每行一条「标签：原值 → 新值」。
     *
     * @return 没有明细时返回 {@code null}，由调用方退回路径推断出的目标信息
     */
    public static String describe(List<Change> changes) {
        if (changes == null || changes.isEmpty()) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        int written = 0;
        for (Change change : changes) {
            String line = change.label() + "：" + display(change.before()) + " → " + display(change.after());
            if (written == 0) {
                out.append(line);
            } else if (out.length() + 1 + line.length() > MAX_DETAIL_LENGTH) {
                break;
            } else {
                out.append('\n').append(line);
            }
            written++;
        }
        if (written < changes.size()) {
            out.append("\n…（共 ").append(changes.size()).append(" 项，其余省略）");
        }
        return out.toString();
    }

    /**
     * 单值转成一行可读文本：多行取值（列表型配置）压成一行，超长截断，空白显示为「（空）」。
     *
     * <p>多行必须压平：{@code detail} 里换行是「条目分隔符」，列表型配置自带换行，不压平会把一条
     * 变更拆成好几条，读日志的人根本分不清哪行是值、哪行是下一条变更。</p>
     */
    private static String display(String raw) {
        if (raw == null) {
            return EMPTY_VALUE;
        }
        String value = raw.replace("\r\n", "\n").replace('\r', '\n').replace("\n", "\\n");
        if (value.isBlank()) {
            return EMPTY_VALUE;
        }
        return value.length() > MAX_VALUE_LENGTH ? value.substring(0, MAX_VALUE_LENGTH) + "…" : value;
    }

    @SuppressWarnings("unchecked")
    private static List<Change> changesMutable(ServletRequest request) {
        Object existing = request.getAttribute(ATTR_CHANGES);
        if (existing instanceof List<?> list) {
            return (List<Change>) list;
        }
        List<Change> created = new ArrayList<>();
        request.setAttribute(ATTR_CHANGES, created);
        return created;
    }
}
