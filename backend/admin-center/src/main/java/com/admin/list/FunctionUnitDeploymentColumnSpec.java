package com.admin.list;

import com.admin.enums.DeploymentStatus;
import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Function Unit deployment-records columns. Outer aliases {@code d} (deployments) and
 * {@code fu} (unit). {@code deployedBy} is a USER id; cells resolve the display name after load.
 */
public final class FunctionUnitDeploymentColumnSpec {

    private FunctionUnitDeploymentColumnSpec() {
    }

    public static List<ListColumnMeta> columns() {
        return List.of(
                ListColumnMeta.of("functionUnitName", "menu.functionUnit", Kind.TEXT),
                ListColumnMeta.of("version", "functionUnit.version", Kind.TEXT),
                ListColumnMeta.withOptions("status", "common.status", Kind.ENUM, statusOptions()),
                ListColumnMeta.of("deployedAt", "functionUnit.deployedAt", Kind.DATETIME),
                ListColumnMeta.of("deployedBy", "functionUnit.deployedBy", Kind.USER)
        );
    }

    public static ListFilterSql sql() {
        Map<String, ListColumnMeta> byField = new LinkedHashMap<>();
        for (ListColumnMeta column : columns()) {
            byField.put(column.field(), column);
        }
        return new ListFilterSql(byField, FunctionUnitDeploymentColumnSpec::sqlFor, "d.id",
                "d.created_at DESC");
    }

    static String sqlFor(String field) {
        return switch (field) {
            case "functionUnitName" -> "fu.name";
            case "version" -> "fu.version";
            case "status" -> "d.status";
            case "deployedAt" -> "COALESCE(d.deployed_at, d.completed_at, d.started_at)";
            case "deployedBy" -> "d.deployed_by";
            default -> throw new IllegalArgumentException("Unknown deployment column: " + field);
        };
    }

    private static List<ListColumnMeta.Option> statusOptions() {
        return Arrays.stream(DeploymentStatus.values())
                .map(status -> new ListColumnMeta.Option(status.name(), status.name()))
                .toList();
    }
}
