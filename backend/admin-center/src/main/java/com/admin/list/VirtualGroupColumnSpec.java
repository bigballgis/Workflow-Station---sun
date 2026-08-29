package com.admin.list;

import com.admin.enums.RoleType;
import com.admin.enums.VirtualGroupType;
import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import com.platform.common.list.ListFilterSql;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Virtual Group columns. Toolbar tab ({@code type}) and keyword stay outside this spec.
 * Outer alias is {@code vg} on {@code sys_virtual_groups}, left-joined to the bound role.
 */
public final class VirtualGroupColumnSpec {

    private VirtualGroupColumnSpec() {
    }

    static final String MEMBER_COUNT_SQL =
            "(SELECT COUNT(*)::text FROM sys_virtual_group_members m WHERE m.group_id = vg.id)";

    public static List<ListColumnMeta> columns() {
        return List.of(
                ListColumnMeta.of("name", "virtualGroup.name", Kind.TEXT),
                ListColumnMeta.of("code", "virtualGroup.code", Kind.TEXT),
                ListColumnMeta.withOptions("type", "virtualGroup.type", Kind.ENUM, typeOptions()),
                ListColumnMeta.of("boundRoleName", "virtualGroup.boundRole", Kind.TEXT),
                ListColumnMeta.withOptions("boundRoleType", "virtualGroup.boundRoleType", Kind.ENUM, roleTypeOptions()),
                ListColumnMeta.of("adGroup", "virtualGroup.adGroup", Kind.TEXT),
                ListColumnMeta.of("memberCount", "virtualGroup.memberCount", Kind.NUMBER),
                ListColumnMeta.withOptions("status", "virtualGroup.status", Kind.ENUM, statusOptions())
        );
    }

    public static ListFilterSql sql() {
        Map<String, ListColumnMeta> byField = new LinkedHashMap<>();
        for (ListColumnMeta column : columns()) {
            byField.put(column.field(), column);
        }
        return new ListFilterSql(byField, VirtualGroupColumnSpec::sqlFor, "vg.id", "vg.name ASC");
    }

    static String sqlFor(String field) {
        return switch (field) {
            case "name" -> "vg.name";
            case "code" -> "vg.code";
            case "type" -> "vg.type";
            case "boundRoleName" -> "r.name";
            case "boundRoleType" -> "r.type";
            case "adGroup" -> "vg.ad_group";
            case "memberCount" -> MEMBER_COUNT_SQL;
            case "status" -> "vg.status";
            default -> throw new IllegalArgumentException("Unknown virtual-group column: " + field);
        };
    }

    private static List<ListColumnMeta.Option> typeOptions() {
        return Arrays.stream(VirtualGroupType.values())
                .map(type -> new ListColumnMeta.Option(type.name(), "virtualGroup.type" + title(type.name())))
                .toList();
    }

    private static List<ListColumnMeta.Option> roleTypeOptions() {
        return Arrays.stream(RoleType.values())
                .map(type -> new ListColumnMeta.Option(type.name(), roleTypeKey(type)))
                .toList();
    }

    private static List<ListColumnMeta.Option> statusOptions() {
        return List.of(
                new ListColumnMeta.Option("ACTIVE", "virtualGroup.active"),
                new ListColumnMeta.Option("INACTIVE", "virtualGroup.inactive"),
                new ListColumnMeta.Option("EXPIRED", "virtualGroup.expired")
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

    private static String title(String name) {
        return name.charAt(0) + name.substring(1).toLowerCase();
    }
}
