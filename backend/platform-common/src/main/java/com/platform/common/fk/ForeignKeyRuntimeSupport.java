package com.platform.common.fk;

import com.platform.common.jdbc.SubTableRowKeySupport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Shared FK/PK runtime helpers for Preview, Portal, and AC data pages (PRD §7, §5.3).
 */
public final class ForeignKeyRuntimeSupport {

    private ForeignKeyRuntimeSupport() {
    }

    public record FieldFkMeta(
            String fieldName,
            boolean foreignKey,
            Long refTableId,
            List<String> refPrimaryKeyFields,
            String fkDisplayMode) {
    }

    public record RowAddContext(
            Map<String, Object> primaryFormData,
            Map<Long, Map<String, Object>> ancestorRowsByTableId) {
    }

    /**
     * Encode parent row PK values into a single FK scalar (composite PK uses U+001F encoding).
     */
    public static String encodeForeignKeyValue(List<String> refPkFields, Map<String, Object> parentRow) {
        if (refPkFields == null || refPkFields.isEmpty() || parentRow == null) {
            return null;
        }
        List<String> ordered = new ArrayList<>(refPkFields);
        Collections.sort(ordered);
        if (ordered.size() == 1) {
            Object v = SubTableRowKeySupport.getRowValueIgnoreCase(parentRow, ordered.get(0));
            return v != null ? String.valueOf(v) : null;
        }
        return SubTableRowKeySupport.canonicalRowKeyString(ordered, parentRow);
    }

    /**
     * Resolve FK column values for a new child row from context.
     */
    public static Map<String, String> resolveForeignKeyValues(
            List<FieldFkMeta> fkMetas,
            RowAddContext ctx) {
        if (fkMetas == null || fkMetas.isEmpty() || ctx == null) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (FieldFkMeta meta : fkMetas) {
            if (meta == null || !meta.foreignKey() || meta.fieldName() == null) {
                continue;
            }
            Map<String, Object> parentRow = resolveParentRow(meta.refTableId(), ctx);
            if (parentRow == null) {
                continue;
            }
            String encoded = encodeForeignKeyValue(meta.refPrimaryKeyFields(), parentRow);
            if (encoded != null) {
                out.put(meta.fieldName(), encoded);
            }
        }
        return out;
    }

    private static Map<String, Object> resolveParentRow(Long refTableId, RowAddContext ctx) {
        if (refTableId == null || ctx == null) {
            return null;
        }
        if (ctx.ancestorRowsByTableId() != null) {
            Map<String, Object> row = ctx.ancestorRowsByTableId().get(refTableId);
            if (row != null) {
                return row;
            }
        }
        return ctx.primaryFormData();
    }

    /**
     * Returns missing parent display info when FK cannot be resolved (guard before opening add dialog).
     */
    public static List<String> guardBeforeChildRowAdd(
            List<FieldFkMeta> fkMetas,
            RowAddContext ctx) {
        if (fkMetas == null || fkMetas.isEmpty()) {
            return List.of();
        }
        List<String> missing = new ArrayList<>();
        for (FieldFkMeta meta : fkMetas) {
            if (meta == null || !meta.foreignKey()) {
                continue;
            }
            Map<String, Object> parentRow = resolveParentRow(meta.refTableId(), ctx);
            if (parentRow == null || parentRow.isEmpty()) {
                missing.add(meta.fieldName());
                continue;
            }
            List<String> pkFields = meta.refPrimaryKeyFields() != null ? meta.refPrimaryKeyFields() : List.of();
            if (!pkFields.isEmpty() && !SubTableRowKeySupport.isComplete(pkFields, parentRow)) {
                missing.add(meta.fieldName());
            }
        }
        return missing;
    }

    public static boolean isHiddenFk(FieldFkMeta meta) {
        return meta != null && meta.foreignKey()
                && Objects.equals("hidden", meta.fkDisplayMode());
    }

    public static boolean isReadonlyFk(FieldFkMeta meta) {
        return meta != null && meta.foreignKey()
                && (meta.fkDisplayMode() == null
                || Objects.equals("readonly", meta.fkDisplayMode())
                || meta.fkDisplayMode().isBlank());
    }
}
