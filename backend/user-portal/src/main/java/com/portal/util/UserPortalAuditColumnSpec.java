package com.portal.util;

import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import com.platform.common.list.ListFilterSql;
import com.portal.enums.ChangeType;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin User Portal audit columns. Toolbar date/operator/FU/change-type/process keyword
 * stay outside this spec and AND with the shared-header filters. Outer alias is {@code ch}
 * so USER filters can still bind {@code sys_users u}.
 */
public final class UserPortalAuditColumnSpec {

    private UserPortalAuditColumnSpec() {
    }

    public static List<ListColumnMeta> columns() {
        return List.of(
                ListColumnMeta.withOptions("changeType", "upAudit.changeType", Kind.ENUM, changeTypeOptions()),
                ListColumnMeta.of("functionUnitCode", "upAudit.functionUnit", Kind.TEXT),
                ListColumnMeta.of("processInstanceId", "upAudit.processInstanceId", Kind.TEXT),
                ListColumnMeta.of("stageId", "upAudit.stage", Kind.TEXT),
                ListColumnMeta.of("subTableName", "upAudit.subTableName", Kind.TEXT),
                ListColumnMeta.of("fieldName", "upAudit.fieldName", Kind.TEXT),
                ListColumnMeta.of("oldValue", "upAudit.oldValue", Kind.TEXT),
                ListColumnMeta.of("newValue", "upAudit.newValue", Kind.TEXT),
                ListColumnMeta.of("userName", "audit.operator", Kind.USER),
                ListColumnMeta.of("timestamp", "audit.time", Kind.DATETIME)
        );
    }

    public static ListFilterSql sql() {
        Map<String, ListColumnMeta> byField = new LinkedHashMap<>();
        for (ListColumnMeta column : columns()) {
            byField.put(column.field(), column);
        }
        return new ListFilterSql(byField, UserPortalAuditColumnSpec::sqlFor, "ch.id", "ch.timestamp DESC");
    }

    static String sqlFor(String field) {
        return switch (field) {
            case "changeType" -> "ch.change_type";
            case "functionUnitCode" -> "pi.function_unit_code";
            case "processInstanceId" -> "ch.process_instance_id";
            case "stageId" -> "ch.stage_id";
            case "subTableName" -> "ch.sub_table_name";
            case "fieldName" -> "ch.field_name";
            case "oldValue" -> "ch.old_value";
            case "newValue" -> "ch.new_value";
            case "userName" -> "ch.user_id";
            case "timestamp" -> "ch.timestamp::text";
            default -> throw new IllegalArgumentException("Unknown user-portal-audit column: " + field);
        };
    }

    private static List<ListColumnMeta.Option> changeTypeOptions() {
        return Arrays.stream(ChangeType.values())
                .map(type -> new ListColumnMeta.Option(type.name(), "upAudit.action" + type.name()))
                .toList();
    }
}
