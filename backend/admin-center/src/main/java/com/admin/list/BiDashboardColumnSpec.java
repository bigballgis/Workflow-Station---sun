package com.admin.list;

import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import com.platform.common.list.ListFilterSql;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BI Dashboard Registry columns. Toolbar title/tags/status stay outside this spec
 * and AND with header filters. Outer alias is {@code d}.
 */
public final class BiDashboardColumnSpec {

    private BiDashboardColumnSpec() {
    }

    public static List<ListColumnMeta> columns() {
        return List.of(
                ListColumnMeta.of("dashboardTitle", "bi.dashboard.colDashboardTitle", Kind.TEXT),
                ListColumnMeta.of("embedId", "bi.dashboard.colEmbedId", Kind.TEXT),
                ListColumnMeta.of("supersetDashboardUuid", "bi.dashboard.colSupersetUuid", Kind.TEXT),
                ListColumnMeta.of("tags", "bi.dashboard.colTags", Kind.TEXT),
                ListColumnMeta.of("isDefaultLanding", "bi.dashboard.colDefaultLanding", Kind.BOOLEAN),
                ListColumnMeta.withOptions("status", "bi.dashboard.colStatus", Kind.ENUM, statusOptions()),
                ListColumnMeta.of("lastSyncedAt", "bi.dashboard.colLastSynced", Kind.DATETIME)
        );
    }

    public static ListFilterSql sql() {
        Map<String, ListColumnMeta> byField = new LinkedHashMap<>();
        for (ListColumnMeta column : columns()) {
            byField.put(column.field(), column);
        }
        return new ListFilterSql(byField, BiDashboardColumnSpec::sqlFor, "d.id", "d.last_synced_at DESC");
    }

    static String sqlFor(String field) {
        return switch (field) {
            case "dashboardTitle" -> "d.dashboard_title";
            case "embedId" -> "d.embed_id::text";
            case "supersetDashboardUuid" -> "d.superset_dashboard_uuid::text";
            case "tags" -> "d.tags";
            case "isDefaultLanding" -> "d.is_default_landing::text";
            case "status" -> "d.status";
            case "lastSyncedAt" -> "d.last_synced_at::text";
            default -> throw new IllegalArgumentException("Unknown bi-dashboard column: " + field);
        };
    }

    private static List<ListColumnMeta.Option> statusOptions() {
        return List.of(
                new ListColumnMeta.Option("ACTIVE", "bi.dashboard.statusActive"),
                new ListColumnMeta.Option("MANUAL_INACTIVE", "bi.dashboard.statusManualInactive"),
                new ListColumnMeta.Option("AUTO_INACTIVE", "bi.dashboard.statusAutoInactive")
        );
    }
}
