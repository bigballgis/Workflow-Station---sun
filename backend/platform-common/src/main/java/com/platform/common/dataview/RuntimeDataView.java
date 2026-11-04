package com.platform.common.dataview;

import java.util.List;
import java.util.Map;

/** Admin-owned release definition; numeric IDs identify logical views across releases. */
public record RuntimeDataView(
        Long id, Long functionUnitId, Long tableId, String viewKey, String catalogId,
        String functionUnitCode, String functionUnitName, String version, String iconSvg,
        String tableName, String tableLabel, String tableType,
        Map<String, Object> definition,
        List<Map<String, Object>> tables, List<Map<String, Object>> forms,
        List<Reference> references, boolean systemAdministrator) {
    public record Reference(Long id, String functionUnitCode, String tableName, boolean defaultView) { }
}
