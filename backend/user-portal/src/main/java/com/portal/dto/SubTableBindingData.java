package com.portal.dto;

import lombok.Data;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * 子表绑定数据 DTO
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
}
