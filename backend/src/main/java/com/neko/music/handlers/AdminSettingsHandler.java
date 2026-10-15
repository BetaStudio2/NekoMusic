package com.neko.music.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.neko.music.Main;
import com.neko.music.config.SystemSettingDefinition;
import com.neko.music.config.SystemSettingRegistry;
import com.neko.music.database.SystemSettingsDatabaseManager;
import com.neko.music.model.Admin;
import com.neko.music.util.AdminPermissionUtil;
import com.neko.music.util.OperationAudit;
import com.neko.music.util.PermissionHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import com.neko.music.i18n.ApiMessages;

/**
 * 后台「系统设置」：system_settings 表里运行时配置的读取与修改，需要管理员及以上权限
 * （审核员不可见）。
 *
 * <ul>
 *   <li>{@code GET  /api/admin/settings} —— 按分组返回全部设置项、当前值与类型元信息</li>
 *   <li>{@code PUT  /api/admin/settings} —— 批量保存：{@code {"values": {"键": "值"}}}</li>
 * </ul>
 *
 * <p>可读写的键由 {@link SystemSettingRegistry} 决定；未登记的键一律拒绝（策略项与启动引导信息
 * 都不在清单里，因此既看不到也改不了）。敏感项只写不读：读取时只返回「是否已配置」，
 * 保存时未提交的键保持原值、提交空串表示清空。</p>
 */
public class AdminSettingsHandler extends ApiServlet {

    private static final Logger logger = LoggerFactory.getLogger(AdminSettingsHandler.class);

    private static final int MAX_STRING_LENGTH = 8192;
    private static final int MAX_LIST_ITEMS = 200;
    private static final int MAX_LIST_ITEM_LENGTH = 512;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!PermissionHelper.checkPermission(request, response,
                AdminPermissionUtil.Permission.SETTINGS_VIEW)) {
            return;
        }
        Map<String, String> stored = settingsDatabase().loadAll();
        ObjectNode data = MAPPER.createObjectNode();
        ArrayNode groups = data.putArray("groups");
        for (SystemSettingDefinition.Group group : SystemSettingDefinition.Group.values()) {
            ArrayNode items = MAPPER.createArrayNode();
            for (SystemSettingDefinition definition : SystemSettingRegistry.all()) {
                if (definition.group() == group) {
                    items.add(describe(definition, stored));
                }
            }
            if (items.isEmpty()) {
                continue;
            }
            ObjectNode node = groups.addObject();
            node.put("key", group.name());
            node.put("title", group.getTitle());
            node.set("settings", items);
        }
        sendJson(response, HttpServletResponse.SC_OK, true, "ok", data);
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!PermissionHelper.checkPermission(request, response,
                AdminPermissionUtil.Permission.SETTINGS_EDIT)) {
            return;
        }

        JsonNode root;
        try {
            root = MAPPER.readTree(readBody(request));
        } catch (Exception e) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "net.invalid_json");
            return;
        }
        JsonNode valuesNode = root == null ? null : root.get("values");
        if (valuesNode == null || !valuesNode.isObject() || valuesNode.isEmpty()) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "admin.settings.values_required");
            return;
        }

        Map<String, String> stored = settingsDatabase().loadAll();
        Map<String, String> updates = new LinkedHashMap<>();
        Set<String> restartKeys = new LinkedHashSet<>();

        Iterator<Map.Entry<String, JsonNode>> fields = valuesNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = field.getKey();
            Optional<SystemSettingDefinition> found = SystemSettingRegistry.find(key);
            if (found.isEmpty()) {
                sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, ApiMessages.translate("admin.settings.unknown_key", key));
                return;
            }
            SystemSettingDefinition definition = found.get();
            JsonNode rawNode = field.getValue();
            String raw = rawNode == null || rawNode.isNull() ? null : rawNode.asText();

            String normalized;
            if (definition.secret()) {
                // 敏感项：不提交 = 保持原值；提交空串 = 清空
                if (raw == null) {
                    continue;
                }
                normalized = raw;
            } else {
                if (raw == null) {
                    sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                            ApiMessages.translate("admin.settings.value_required", definition.label()));
                    return;
                }
                String error = validate(definition, raw);
                if (error != null) {
                    sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, error);
                    return;
                }
                normalized = normalize(definition, raw);
            }

            updates.put(key, normalized);
            if (definition.restartRequired()
                    && !normalized.equals(stored.getOrDefault(key, ""))) {
                restartKeys.add(key);
            }
        }

        if (updates.isEmpty()) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "admin.settings.nothing_to_save");
            return;
        }

        // 先把「改了什么」挂到请求属性上：操作日志过滤器在响应写出后才落库，这里不记录就只剩
        // 「修改系统设置」一句空话，翻日志时看不出到底动了哪一项。
        recordChanges(request, stored, updates);

        Admin admin = PermissionHelper.getAdminFromRequest(request);
        try {
            int written = settingsDatabase().upsertAll(updates, admin == null ? null : admin.id());
            // 立即重新叠加一次，读取类配置无需重启即可生效；启动期绑定的项仍以 restartRequired 提示
            Main.getConfigManager().applyOverrides(settingsDatabase().loadAll());
            logger.info("管理员 {} 更新了 {} 条系统设置", admin == null ? "?" : admin.username(), written);

            ObjectNode data = MAPPER.createObjectNode();
            data.put("updated", written);
            ArrayNode restart = data.putArray("restartRequired");
            restartKeys.forEach(restart::add);
            sendJson(response, HttpServletResponse.SC_OK, true, "admin.settings.saved", data);
        } catch (SQLException e) {
            logger.error("保存系统设置失败", e);
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "admin.settings.save_failed");
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_OK);
    }

    /** 组装单个设置项：敏感项不回传明文，只回传「是否已配置」。 */
    private ObjectNode describe(SystemSettingDefinition definition, Map<String, String> stored) {
        ObjectNode item = MAPPER.createObjectNode();
        item.put("key", definition.key());
        item.put("label", definition.label());
        item.put("type", definition.type().name().toLowerCase(Locale.ROOT));
        item.put("secret", definition.secret());
        item.put("restartRequired", definition.restartRequired());
        item.put("description", definition.description());
        if (definition.min() != null) {
            item.put("min", definition.min());
        }
        if (definition.max() != null) {
            item.put("max", definition.max());
        }
        String effective = effectiveValue(definition, stored);
        if (definition.secret()) {
            item.put("configured", !effective.isBlank());
        } else {
            item.put("value", effective);
            item.put("defaultValue", definition.defaultValue());
        }
        return item;
    }

    /**
     * 把「改了哪些设置项、原来是什么、改成了什么」挂到请求属性上，供操作日志落库。
     *
     * <p>原值取「当前生效值」（库里优先，其次 config.yml，最后出厂默认值）——后台页面上给管理员看的
     * 就是这个值，日志与页面对不上才是最糟的。只记真正变化的项：前端本来就只提交改动过的键，
     * 这里再按值过滤一次，避免「改了又改回去」留下一条什么也没改的日志。</p>
     *
     * <p>敏感项不落明文：只记状态迁移（已配置 / 已更新 / 已清空），不记新旧取值。</p>
     */
    private static void recordChanges(HttpServletRequest request, Map<String, String> stored,
                                      Map<String, String> updates) {
        for (OperationAudit.Change change : settingChanges(stored, updates)) {
            OperationAudit.add(request, change.label(), change.before(), change.after());
        }
    }

    /**
     * 纯函数版变更明细：把「提交的键值」与「当前生效值」比出差异，方便单测直接断言。
     *
     * @param stored  库里已保存的值
     * @param updates 归一化后的待保存值（前端只提交改动过的键）
     * @return 只包含真正发生变化的项；未登记的键被忽略
     */
    static List<OperationAudit.Change> settingChanges(Map<String, String> stored, Map<String, String> updates) {
        List<OperationAudit.Change> changes = new ArrayList<>();
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            SystemSettingDefinition definition = SystemSettingRegistry.find(entry.getKey()).orElse(null);
            if (definition == null) {
                continue;
            }
            String after = entry.getValue();
            if (definition.secret()) {
                boolean configured = !stored.getOrDefault(entry.getKey(), "").isBlank();
                String afterState = ApiMessages.translate(after.isBlank()
                        ? "admin.settings.secret_cleared"
                        : (configured ? "admin.settings.secret_updated_hidden" : "admin.settings.secret_configured"));
                changes.add(new OperationAudit.Change(definition.label(),
                        ApiMessages.translate(configured
                                ? "admin.settings.secret_configured"
                                : "admin.settings.secret_not_configured"), afterState));
                continue;
            }
            String before = effectiveValue(definition, stored);
            if (Objects.equals(before, after)) {
                continue;
            }
            changes.add(new OperationAudit.Change(definition.label(), before, after));
        }
        return changes;
    }

    /** 当前生效值：优先数据库，其次 config.yml，最后出厂默认值。 */
    private static String effectiveValue(SystemSettingDefinition definition, Map<String, String> stored) {
        String value = stored.get(definition.key());
        if (value != null) {
            return value;
        }
        return Main.getConfigManager().effectiveValue(definition.key())
                .orElseGet(definition::defaultValue);
    }

    /** 校验取值；返回 null 表示通过，否则返回给管理员看的原因。 */
    private static String validate(SystemSettingDefinition definition, String raw) {
        String value = raw == null ? "" : raw;
        switch (definition.type()) {
            case STRING -> {
                if (value.length() > MAX_STRING_LENGTH) {
                    return ApiMessages.translate("admin.settings.too_long", definition.label());
                }
            }
            case BOOL -> {
                String normalized = value.trim().toLowerCase(Locale.ROOT);
                if (!normalized.equals("true") && !normalized.equals("false")) {
                    return ApiMessages.translate("admin.settings.bool_required", definition.label());
                }
            }
            case INT, LONG -> {
                if (value.isBlank()) {
                    return null;
                }
                long parsed;
                try {
                    parsed = definition.type() == SystemSettingDefinition.Type.INT
                            ? Integer.parseInt(value.trim())
                            : Long.parseLong(value.trim());
                } catch (NumberFormatException e) {
                    return ApiMessages.translate("admin.settings.int_required", definition.label());
                }
                return checkRange(definition, parsed);
            }
            case DOUBLE -> {
                if (value.isBlank()) {
                    return null;
                }
                double parsed;
                try {
                    parsed = Double.parseDouble(value.trim());
                } catch (NumberFormatException e) {
                    return ApiMessages.translate("admin.settings.number_required", definition.label());
                }
                if (!Double.isFinite(parsed)) {
                    return ApiMessages.translate("admin.settings.finite_number_required", definition.label());
                }
                if (definition.min() != null && parsed < definition.min()) {
                    return ApiMessages.translate("admin.settings.below_min", definition.label(), definition.min());
                }
                if (definition.max() != null && parsed > definition.max()) {
                    return ApiMessages.translate("admin.settings.above_max", definition.label(), definition.max());
                }
            }
            case LIST -> {
                String[] lines = value.split("\\r?\\n");
                if (lines.length > MAX_LIST_ITEMS) {
                    return ApiMessages.translate("admin.settings.list_too_many", definition.label(), MAX_LIST_ITEMS);
                }
                for (String line : lines) {
                    if (line.trim().length() > MAX_LIST_ITEM_LENGTH) {
                        return ApiMessages.translate("admin.settings.list_item_too_long", definition.label());
                    }
                }
            }
        }
        return null;
    }

    private static String checkRange(SystemSettingDefinition definition, long parsed) {
        if (definition.min() != null && parsed < definition.min()) {
            return ApiMessages.translate("admin.settings.below_min", definition.label(), definition.min());
        }
        if (definition.max() != null && parsed > definition.max()) {
            return ApiMessages.translate("admin.settings.above_max", definition.label(), definition.max());
        }
        return null;
    }

    /** 统一存储形态：布尔转小写，数值去空白，列表去空行。 */
    private static String normalize(SystemSettingDefinition definition, String raw) {
        String value = raw == null ? "" : raw;
        return switch (definition.type()) {
            case BOOL -> value.trim().toLowerCase(Locale.ROOT);
            case INT, LONG, DOUBLE -> value.trim();
            case STRING -> value;
            case LIST -> {
                StringBuilder sb = new StringBuilder();
                for (String line : value.split("\\r?\\n")) {
                    String item = line.trim();
                    if (item.isEmpty()) {
                        continue;
                    }
                    if (sb.length() > 0) {
                        sb.append('\n');
                    }
                    sb.append(item);
                }
                yield sb.toString();
            }
        };
    }

    private static SystemSettingsDatabaseManager settingsDatabase() {
        return Main.getSystemSettingsDatabaseManager();
    }
}
