package com.admin.list;

import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fixed column declaration for the Admin User list. Toolbar keyword/status stay outside this
 * spec and AND with the shared-header filters. Outer table alias is {@code su} so USER filters
 * can still bind {@code sys_users u} inside {@link ListFilterSql}.
 */
public final class UserColumnSpec {

    private UserColumnSpec() {
    }

    public static List<ListColumnMeta> columns() {
        return List.of(
                ListColumnMeta.of("username", "user.username", Kind.TEXT),
                ListColumnMeta.of("fullName", "user.fullName", Kind.TEXT),
                ListColumnMeta.of("email", "user.email", Kind.TEXT),
                ListColumnMeta.of("position", "user.position", Kind.TEXT),
                ListColumnMeta.of("entityManagerName", "user.entityManager", Kind.USER),
                ListColumnMeta.of("functionManagerName", "user.functionManager", Kind.USER),
                ListColumnMeta.withOptions("status", "common.status", Kind.ENUM, statusOptions())
        );
    }

    public static ListFilterSql sql() {
        Map<String, ListColumnMeta> byField = new LinkedHashMap<>();
        for (ListColumnMeta column : columns()) {
            byField.put(column.field(), column);
        }
        return new ListFilterSql(byField, UserColumnSpec::sqlFor, "su.id", "su.created_at DESC");
    }

    static String sqlFor(String field) {
        return switch (field) {
            case "username" -> "su.username";
            case "fullName" -> "su.full_name";
            case "email" -> "su.email";
            case "position" -> "su.position";
            case "entityManagerName" -> "su.entity_manager_id";
            case "functionManagerName" -> "su.function_manager_id";
            case "status" -> "su.status";
            default -> throw new IllegalArgumentException("Unknown user-list column: " + field);
        };
    }

    private static List<ListColumnMeta.Option> statusOptions() {
        return List.of(
                new ListColumnMeta.Option("ACTIVE", "user.active"),
                new ListColumnMeta.Option("INACTIVE", "user.disabled"),
                new ListColumnMeta.Option("LOCKED", "user.locked")
        );
    }
}
