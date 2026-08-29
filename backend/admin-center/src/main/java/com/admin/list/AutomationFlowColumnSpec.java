package com.admin.list;

import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import com.platform.common.list.ListFilterSql;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Automation Flows columns. Toolbar keyword stays outside this spec.
 * {@code readiness} is DRAFT / ENABLED / DISABLED (same ladder as the catalog page).
 */
public final class AutomationFlowColumnSpec {

    private AutomationFlowColumnSpec() {
    }

    static final String READINESS_SQL = """
            CASE \
            WHEN f."publishedVersionId" IS NULL THEN 'DRAFT' \
            WHEN f.status = 'ENABLED' THEN 'ENABLED' \
            ELSE 'DISABLED' END""";

    static final String OWNER_SQL =
            "nullif(trim(both from concat_ws(' ', ui.\"firstName\", ui.\"lastName\")), '')";

    public static List<ListColumnMeta> columns() {
        return List.of(
                ListColumnMeta.of("displayName", "automationFlow.displayName", Kind.TEXT),
                ListColumnMeta.of("id", "automationFlow.flowId", Kind.TEXT),
                ListColumnMeta.withOptions("readiness", "automationFlow.state", Kind.ENUM, readinessOptions()),
                ListColumnMeta.of("projectName", "automationFlow.project", Kind.TEXT),
                ListColumnMeta.of("ownerName", "automationFlow.owner", Kind.TEXT),
                ListColumnMeta.of("updated", "automationFlow.updated", Kind.DATETIME)
        );
    }

    public static ListFilterSql sql() {
        Map<String, ListColumnMeta> byField = new LinkedHashMap<>();
        for (ListColumnMeta column : columns()) {
            byField.put(column.field(), column);
        }
        return new ListFilterSql(byField, AutomationFlowColumnSpec::sqlFor, "f.id", "fv.updated DESC");
    }

    static String sqlFor(String field) {
        return switch (field) {
            case "displayName" -> "fv.\"displayName\"";
            case "id" -> "f.id";
            case "readiness" -> READINESS_SQL;
            case "projectName" -> "p.\"displayName\"";
            case "ownerName" -> OWNER_SQL;
            case "updated" -> "fv.updated::text";
            default -> throw new IllegalArgumentException("Unknown automation-flow column: " + field);
        };
    }

    private static List<ListColumnMeta.Option> readinessOptions() {
        return List.of(
                new ListColumnMeta.Option("DRAFT", "automationFlow.stateDraft"),
                new ListColumnMeta.Option("ENABLED", "automationFlow.stateLive"),
                new ListColumnMeta.Option("DISABLED", "automationFlow.stateStopped")
        );
    }
}
