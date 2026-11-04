package com.portal.service.impl;

import com.platform.common.dataview.RuntimeDataView;
import java.util.List;
import java.util.Map;

/** Published metadata shared by list queries and request-detail scope checks. */
record ViewDefinition(
        Long id,
        String viewName,
        String functionUnitCode,
        List<Map<String, Object>> sortConfig,
        Map<String, Object> filterConfig,
        List<ViewFieldDef> fields,
        Long mainTableId,
        String tableType,
        String subStoreKey,
        boolean restrictToInvolvedUsers,
        RuntimeDataView release) {

    record ViewFieldDef(
            String fieldName,
            String displayLabel,
            Integer columnWidth,
            Integer sortOrder,
            Boolean visible,
            Boolean systemField,
            String columnType,
            String lookupSourceField,
            String lookupDisplayField,
            String selectDisplay) { }
}
