package com.portal.dto;

import lombok.Data;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * Sub-table binding payload DTO.
 */
@Data
@Builder
public class SubTableBindingData {
    private Long bindingId;
    private String tableName;
    /**
     * Designer-configured display name (dw_table_definitions.table_display_name for SUB/PRIMARY,
     * rt_table_definitions.display_name for RELATED). Portals MUST prefer this over
     * {@link #tableName} when rendering — keeps User Portal aligned with the Developer Workstation
     * Form Preview (portal-design-parity).
     */
    private String tableDisplayName;
    private String bindingType;
    private String bindingMode;
    private Long subListViewId;
    private List<Map<String, Object>> columns;
    private List<Map<String, Object>> data;
    /** Primary key field names from dw_field_definitions.is_primary_key. */
    private List<String> primaryKeyFields;
    /** MI assignment contract (allowUser/allowRole/assigneeField/roleField/buField) parsed from BPMN, keyed by tableName. */
    private Map<String, Object> assignmentConfig;
}
