package com.admin.list;

import com.admin.enums.RoleType;
import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Role List columns. Toolbar tab ({@code SYSTEM}/{@code CUSTOM}) and type stay outside
 * this spec. Outer alias is {@code r} on {@code sys_roles}.
 */
public final class RoleColumnSpec {

    private RoleColumnSpec() {
    }

    /** Same codes as frontend {@code SYSTEM_ROLE_LIST_CODES} (excludes FU_VIEWER). */
    public static final List<String> SYSTEM_ROLE_LIST_CODES = List.of(
            "SYS_ADMIN", "AUDITOR", "TECH_LEAD", "TEAM_LEAD", "DEVELOPER", "MANAGER");

    public static List<ListColumnMeta> columns() {
        return List.of(
                ListColumnMeta.of("name", "role.roleName", Kind.TEXT),
                ListColumnMeta.of("code", "role.roleCode", Kind.TEXT),
                ListColumnMeta.withOptions("type", "role.roleType", Kind.ENUM, typeOptions()),
                ListColumnMeta.withOptions("status", "common.status", Kind.ENUM, statusOptions()),
                ListColumnMeta.of("isSystem", "role.systemRole", Kind.BOOLEAN)
        );
    }

    public static ListFilterSql sql() {
        Map<String, ListColumnMeta> byField = new LinkedHashMap<>();
        for (ListColumnMeta column : columns()) {
            byField.put(column.field(), column);
        }
        return new ListFilterSql(byField, RoleColumnSpec::sqlFor, "r.id", "r.name ASC");
    }

    static String sqlFor(String field) {
        return switch (field) {
            case "name" -> "r.name";
            case "code" -> "r.code";
            case "type" -> "r.type";
            case "status" -> "r.status";
            case "isSystem" -> "r.is_system::text";
            default -> throw new IllegalArgumentException("Unknown role column: " + field);
        };
    }

    private static List<ListColumnMeta.Option> typeOptions() {
        return Arrays.stream(RoleType.values())
                .map(type -> new ListColumnMeta.Option(type.name(), roleTypeKey(type)))
                .toList();
    }

    private static List<ListColumnMeta.Option> statusOptions() {
        return List.of(
                new ListColumnMeta.Option("ACTIVE", "common.enabled"),
                new ListColumnMeta.Option("INACTIVE", "common.disabled")
        );
    }

    private static String roleTypeKey(RoleType type) {
        return switch (type) {
            case BU_BOUNDED -> "role.buBounded";
            case BU_UNBOUNDED -> "role.buUnbounded";
            case ADMIN -> "role.adminRole";
            case AUDITOR -> "role.auditorRole";
            case DEVELOPER -> "role.developerRole";
        };
    }
}
