package com.admin.list;

import com.admin.enums.FunctionUnitStatus;
import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import com.platform.common.list.ListFilterSql;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Function Unit list / archive columns. Keyword (name/code/description) stays on the toolbar.
 * Latest-version-per-code is applied in SQL before these filters.
 */
public final class FunctionUnitColumnSpec {

    private FunctionUnitColumnSpec() {
    }

    /**
     * Pick the highest semantic version per {@code code}. Numeric parts only; non-numeric
     * segments sort as 0, matching the frontend {@code compareVersions} pad-with-zero rule.
     */
    public static final String VERSION_ORDER_SQL = """
            CASE WHEN split_part(fu.version, '.', 1) ~ '^[0-9]+$' \
            THEN split_part(fu.version, '.', 1)::int ELSE 0 END DESC, \
            CASE WHEN split_part(fu.version, '.', 2) ~ '^[0-9]+$' \
            THEN split_part(fu.version, '.', 2)::int ELSE 0 END DESC, \
            CASE WHEN split_part(fu.version, '.', 3) ~ '^[0-9]+$' \
            THEN split_part(fu.version, '.', 3)::int ELSE 0 END DESC\
            """;

    public static List<ListColumnMeta> columns() {
        return List.of(
                ListColumnMeta.of("name", "common.name", Kind.TEXT),
                ListColumnMeta.of("code", "common.code", Kind.TEXT),
                ListColumnMeta.of("version", "functionUnit.version", Kind.TEXT),
                ListColumnMeta.withOptions("status", "common.status", Kind.ENUM, statusOptions(false)),
                ListColumnMeta.of("enabled", "common.enable", Kind.BOOLEAN),
                ListColumnMeta.of("updatedAt", "common.updateTime", Kind.DATETIME)
        );
    }

    public static List<ListColumnMeta> archiveColumns() {
        return List.of(
                ListColumnMeta.of("name", "common.name", Kind.TEXT),
                ListColumnMeta.of("code", "common.code", Kind.TEXT),
                ListColumnMeta.of("version", "functionUnit.version", Kind.TEXT),
                ListColumnMeta.withOptions("status", "common.status", Kind.ENUM, statusOptions(true)),
                ListColumnMeta.of("updatedAt", "common.updateTime", Kind.DATETIME),
                ListColumnMeta.of("updatedBy", "common.updatedBy", Kind.USER)
        );
    }

    public static ListFilterSql sql() {
        return sqlFor(columns());
    }

    public static ListFilterSql archiveSql() {
        return sqlFor(archiveColumns());
    }

    public static String latestFrom(boolean archived) {
        String statusPredicate = archived
                ? "fu.status = 'ARCHIVED'"
                : "fu.status <> 'ARCHIVED'";
        return " FROM ("
                + " SELECT DISTINCT ON (fu.code) fu.*"
                + " FROM sys_function_units fu"
                + " WHERE " + statusPredicate
                + " ORDER BY fu.code, " + VERSION_ORDER_SQL + ", fu.updated_at DESC NULLS LAST"
                + ") fu WHERE 1=1 ";
    }

    private static ListFilterSql sqlFor(List<ListColumnMeta> columns) {
        Map<String, ListColumnMeta> byField = new LinkedHashMap<>();
        for (ListColumnMeta column : columns) {
            byField.put(column.field(), column);
        }
        return new ListFilterSql(byField, FunctionUnitColumnSpec::sqlExpr, "fu.id",
                "fu.updated_at DESC NULLS LAST, fu.id DESC");
    }

    static String sqlExpr(String field) {
        return switch (field) {
            case "name" -> "fu.name";
            case "code" -> "fu.code";
            case "version" -> "fu.version";
            case "status" -> "fu.status";
            case "enabled" -> "fu.enabled::text";
            case "updatedAt" -> "fu.updated_at";
            case "updatedBy" -> "fu.updated_by";
            default -> throw new IllegalArgumentException("Unknown function-unit column: " + field);
        };
    }

    private static List<ListColumnMeta.Option> statusOptions(boolean archiveOnly) {
        return Arrays.stream(FunctionUnitStatus.values())
                .filter(status -> archiveOnly == (status == FunctionUnitStatus.ARCHIVED))
                .map(status -> new ListColumnMeta.Option(status.name(), statusI18nKey(status)))
                .collect(Collectors.toList());
    }

    private static String statusI18nKey(FunctionUnitStatus status) {
        return switch (status) {
            case DRAFT -> "functionUnit.statusDraft";
            case VALIDATED -> "functionUnit.statusValidated";
            case DEPLOYED -> "functionUnit.statusDeployed";
            case DEPRECATED -> "functionUnit.statusDeprecated";
            case ARCHIVED -> "functionUnit.statusArchived";
        };
    }
}
