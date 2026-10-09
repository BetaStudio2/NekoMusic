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
import com.neko.music.util.PermissionHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 后台「系统设置」：system_settings 表里运行时配置的读取与修改，需要管理员及以上权限
 * （审核员不可见）。
 *
 * <ul>
 *   <li>{@code GET  /api/admin/settings} —— 按分组返回全部设置项、当前值与类型元信息</li>
 *   <li>{@code PUT  /api/admin/settings} —— 批量保存：{@code {"values": {"键": "值"}}}</li>
 * </ul>
 *
 * <p>可读写的键由 {@link SystemSettingRegistry} 决定；未登记的键、以及登记为只读的键一律拒绝。
 * 敏感项只写不读：读取时只返回「是否已配置」，保存时未提交的键保持原值、提交空串表示清空。
 * 只读项只在读取时回显当前值并带 {@code readOnly: true}，供前端禁用控件。</p>
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
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "JSON 格式无效");
            return;
        }
        JsonNode valuesNode = root == null ? null : root.get("values");
        if (valuesNode == null || !valuesNode.isObject() || valuesNode.isEmpty()) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "缺少 values 对象");
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
                sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "未知的设置项: " + key);
                return;
            }
            SystemSettingDefinition definition = found.get();
            if (definition.readOnly()) {
                sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                        definition.label() + " 不允许修改");
                return;
            }
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
                            definition.label() + " 缺少取值");
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
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "没有需要保存的设置项");
            return;
        }

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
            sendJson(response, HttpServletResponse.SC_OK, true, "设置已保存", data);
        } catch (SQLException e) {
            logger.error("保存系统设置失败", e);
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "保存失败");
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
        item.put("readOnly", definition.readOnly());
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
                    return definition.label() + " 过长";
                }
            }
            case BOOL -> {
                String normalized = value.trim().toLowerCase(Locale.ROOT);
                if (!normalized.equals("true") && !normalized.equals("false")) {
                    return definition.label() + " 只能是 true 或 false";
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
                    return definition.label() + " 必须是整数";
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
                    return definition.label() + " 必须是数字";
                }
                if (!Double.isFinite(parsed)) {
                    return definition.label() + " 必须是有限数字";
                }
                if (definition.min() != null && parsed < definition.min()) {
                    return definition.label() + " 不能小于 " + definition.min();
                }
                if (definition.max() != null && parsed > definition.max()) {
                    return definition.label() + " 不能大于 " + definition.max();
                }
            }
            case LIST -> {
                String[] lines = value.split("\\r?\\n");
                if (lines.length > MAX_LIST_ITEMS) {
                    return definition.label() + " 条目过多（最多 " + MAX_LIST_ITEMS + " 条）";
                }
                for (String line : lines) {
                    if (line.trim().length() > MAX_LIST_ITEM_LENGTH) {
                        return definition.label() + " 单条过长";
                    }
                }
            }
        }
        return null;
    }

    private static String checkRange(SystemSettingDefinition definition, long parsed) {
        if (definition.min() != null && parsed < definition.min()) {
            return definition.label() + " 不能小于 " + definition.min();
        }
        if (definition.max() != null && parsed > definition.max()) {
            return definition.label() + " 不能大于 " + definition.max();
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
