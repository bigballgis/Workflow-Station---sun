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
            int viewCount
    ) {}

    @Builder
    public record MainTableViewSummary(
            Long id,
            String viewName,
            Boolean isDefault,
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
            List<String> refPrimaryKeyFields
    ) {}

    @Builder
    public record MainTableViewDataRow(
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
