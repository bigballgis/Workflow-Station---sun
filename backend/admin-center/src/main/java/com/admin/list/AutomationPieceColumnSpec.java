package com.admin.list;

import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Automation Pieces columns. One row per package (latest matching version).
 * Toolbar keyword stays outside this spec. Outer alias is {@code pm}
 * on a DISTINCT ON (name) subquery of {@code piece_metadata}.
 */
public final class AutomationPieceColumnSpec {

    private AutomationPieceColumnSpec() {
    }

    static final String DISABLED_SQL =
            "(EXISTS (SELECT 1 FROM hermes_piece_block b WHERE b.\"pieceName\" = pm.name))::text";

    static final String ACTION_COUNT_SQL =
            "(SELECT count(*)::text FROM jsonb_object_keys(COALESCE(pm.actions::jsonb, '{}'::jsonb)))";

    static final String TRIGGER_COUNT_SQL =
            "(SELECT count(*)::text FROM jsonb_object_keys(COALESCE(pm.triggers::jsonb, '{}'::jsonb)))";

    public static List<ListColumnMeta> columns() {
        return List.of(
                ListColumnMeta.of("displayName", "automationPiece.displayName", Kind.TEXT),
                ListColumnMeta.of("name", "automationPiece.packageName", Kind.TEXT),
                ListColumnMeta.of("version", "automationPiece.version", Kind.TEXT),
                ListColumnMeta.withOptions("pieceType", "automationPiece.type", Kind.ENUM, typeOptions()),
                ListColumnMeta.of("disabled", "common.disabled", Kind.BOOLEAN),
                ListColumnMeta.of("actionCount", "automationPiece.actions", Kind.NUMBER),
                ListColumnMeta.of("triggerCount", "automationPiece.triggers", Kind.NUMBER),
                ListColumnMeta.of("updated", "automationPiece.updated", Kind.DATETIME)
        );
    }

    public static ListFilterSql sql() {
        Map<String, ListColumnMeta> byField = new LinkedHashMap<>();
        for (ListColumnMeta column : columns()) {
            byField.put(column.field(), column);
        }
        return new ListFilterSql(byField, AutomationPieceColumnSpec::sqlFor, "pm.name", "pm.name ASC");
    }

    static String sqlFor(String field) {
        return switch (field) {
            case "displayName" -> "pm.\"displayName\"";
            case "name" -> "pm.name";
            case "version" -> "pm.version";
            case "pieceType" -> "pm.\"pieceType\"";
            case "disabled" -> DISABLED_SQL;
            case "actionCount" -> ACTION_COUNT_SQL;
            case "triggerCount" -> TRIGGER_COUNT_SQL;
            case "updated" -> "pm.updated::text";
            default -> throw new IllegalArgumentException("Unknown automation-piece column: " + field);
        };
    }

    private static List<ListColumnMeta.Option> typeOptions() {
        return List.of(
                new ListColumnMeta.Option("OFFICIAL", "OFFICIAL"),
                new ListColumnMeta.Option("CUSTOM", "CUSTOM")
        );
    }
}
