package com.admin.list;

import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BI RBAC mapping columns (one row per mapped sys_role). Toolbar roleName/roleType
 * stay outside this spec. Outer alias is {@code r}.
 */
public final class BiRbacColumnSpec {

    private BiRbacColumnSpec() {
    }

    static final String SUPERSET_ROLES_SQL = """
            (SELECT string_agg(sr.name, ', ' ORDER BY sr.name) \
            FROM bi_rbac_mapping m \
            JOIN bi_superset_role sr ON sr.superset_role_id = m.superset_role_id \
            WHERE m.sys_role_id = r.id)""";

    static final String LAST_UPDATED_SQL =
            "(SELECT MAX(m.created_at)::text FROM bi_rbac_mapping m WHERE m.sys_role_id = r.id)";

    public static List<ListColumnMeta> columns() {
        return List.of(
                ListColumnMeta.of("sysRoleName", "bi.rbac.colSystemRole", Kind.TEXT),
                ListColumnMeta.of("sysRoleCode", "bi.rbac.colRoleCode", Kind.TEXT),
                ListColumnMeta.withOptions("sysRoleType", "bi.rbac.colRoleType", Kind.ENUM, roleTypeOptions()),
                ListColumnMeta.of("supersetRoles", "bi.rbac.colSupersetRoles", Kind.TEXT),
                ListColumnMeta.of("lastUpdatedAt", "bi.rbac.colLastUpdated", Kind.DATETIME)
        );
    }

    public static ListFilterSql sql() {
        Map<String, ListColumnMeta> byField = new LinkedHashMap<>();
        for (ListColumnMeta column : columns()) {
            byField.put(column.field(), column);
        }
        return new ListFilterSql(byField, BiRbacColumnSpec::sqlFor, "r.id", "r.name ASC");
    }

    static String sqlFor(String field) {
        return switch (field) {
            case "sysRoleName" -> "r.name";
            case "sysRoleCode" -> "r.code";
            case "sysRoleType" -> "r.type";
            case "supersetRoles" -> SUPERSET_ROLES_SQL;
            case "lastUpdatedAt" -> LAST_UPDATED_SQL;
            default -> throw new IllegalArgumentException("Unknown bi-rbac column: " + field);
        };
    }

    private static List<ListColumnMeta.Option> roleTypeOptions() {
        return List.of(
                new ListColumnMeta.Option("ADMIN", "role.adminRole"),
                new ListColumnMeta.Option("AUDITOR", "role.auditorRole"),
                new ListColumnMeta.Option("DEVELOPER", "role.developerRole"),
                new ListColumnMeta.Option("BUSINESS", "role.businessRole"),
                new ListColumnMeta.Option("BU_BOUNDED", "role.buBounded"),
                new ListColumnMeta.Option("BU_UNBOUNDED", "role.buUnbounded")
        );
    }
}
