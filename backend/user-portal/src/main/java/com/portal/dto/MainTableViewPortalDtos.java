package com.portal.dto;

import com.platform.common.list.ListColumnMeta;
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
            /** Inline SVG markup from DW icon library; null when none. */
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
            /**
             * Owning table's type (MAIN / SUB). MAIN means a row is a request, so the portal opens
             * the request detail page for it instead of {@code detailFormId}.
             */
            String tableType,
            Boolean enableExport,
            Boolean enableImport,
            /** DETAIL form opened when a row is clicked; null means rows are not clickable. */
            Long detailFormId
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
            ListColumnMeta.Kind kind,
            Boolean filterable,
            Boolean sortable,
            List<String> operators,
            /** Closed choices for ENUM / BOOLEAN; empty for open-value kinds. */
            List<ListColumnMeta.Option> options
    ) {
        /**
         * Copies the list-header contract onto a view column. {@code options} must travel:
         * BOOLEAN / ENUM filters are a closed select, and omitting the list makes the portal
         * throw (or, if that guard is bypassed, fall through to a text box).
         */
        public static MainTableViewFieldColumnBuilder applyListCapabilities(
                MainTableViewFieldColumnBuilder builder, ListColumnMeta cap) {
            return builder
                    .kind(cap.kind())
                    .filterable(cap.filterable())
                    .sortable(cap.sortable())
                    .operators(cap.operators())
                    .options(cap.options());
        }
    }

    /**
     * @param rowKey what makes this row distinct from every other row of the view. On a MAIN view
     *               that is the process instance; on a SUB view one instance contributes many
     *               rows, so the instance id alone would repeat and the grid could not tell two
     *               of them apart when selecting or re-rendering.
     */
    @Builder
    public record MainTableViewDataRow(
            String rowKey,
            String processInstanceId,
            Map<String, Object> values
    ) {}

    @Builder
    public record MainTableViewDataPage(
            List<MainTableViewFieldColumn> columns,
            List<MainTableViewDataRow> rows,
            long total,
            int page,
            int size
    ) {}
}
