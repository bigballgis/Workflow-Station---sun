package com.portal.dto;

import lombok.Builder;

import java.util.List;
import java.util.Map;

public final class MainTableViewPortalDtos {

    private MainTableViewPortalDtos() {}

    @Builder
    public record FunctionUnitViewMenuItem(
            String functionUnitId,
            String functionUnitCode,
            String functionUnitName,
            int viewCount,
            String iconSvg
    ) {}

    @Builder
    public record MainTableViewSummary(
            Long id,
            String viewName,
            Boolean isDefault,
            // Owning table — lets the portal group the view selector by table (parity with View Design).
            Long tableId,
            String tableLabel,
            Boolean enableExport,
            Boolean enableImport
    ) {}

    @Builder
    public record MainTableViewFieldColumn(
            String fieldName,
            String displayLabel,
            Integer columnWidth,
            Boolean systemField,
            // FK drill-down hints: when isForeignKey, the portal renders a link to the referenced
            // table's published default view (refViewId), pre-filtered by this column's value.
            Boolean isForeignKey,
            Long refViewId,
            String refFunctionUnitCode,
            List<String> refPrimaryKeyFields,
            // Lookup drill-down: when isLookup, the portal links to the referenced Relation Table's data
            // (lookupTableId), pre-filtered by this cell's value. Resolved from the form's lookupConfig.
            Boolean isLookup,
            Long lookupTableId,
            /** {@code field}, {@code lookup_display}, or {@code fk_display}. */
            String columnType,
            /** For lookup_display / fk_display: source field on the owning table. */
            String lookupSourceField,
            /** For lookup_display / fk_display: attribute on the related row. */
            String lookupDisplayField,
            /** From form lookupConfig.selectedDisplayField — used to label the source lookup column. */
            String lookupSelectedDisplayField,
            /** From form lookupConfig.searchFields — PK hydration hint for the portal. */
            List<String> lookupSearchFields,
            /** For fk_display: referenced DW table id (from FieldDefinition.refTableId). */
            Long fkRefTableId,
            // What the column header may offer. Declared by the backend because only it knows
            // whether the query can answer that question about this column — see
            // MainTableViewColumnSpec.
            PortalListColumnMeta.Kind kind,
            Boolean filterable,
            Boolean sortable,
            Boolean groupable,
            List<String> operators
    ) {}

    @Builder
    public record MainTableViewDataRow(
            String processInstanceId,
            Map<String, Object> values
    ) {}

    /**
     * A group of the whole result set. Counted by the database over the same predicate as the
     * page, so a header still reads the true size of its group on a page that only holds part
     * of it.
     */
    @Builder
    public record MainTableViewGroup(String label, long count) {}

    @Builder
    public record MainTableViewDataPage(
            List<MainTableViewFieldColumn> columns,
            List<MainTableViewDataRow> rows,
            long total,
            int page,
            int size,
            /** Empty unless the request grouped by a column. */
            List<MainTableViewGroup> groups
    ) {}
}
