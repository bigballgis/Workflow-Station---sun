package com.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 表绑定 DTO
 * 描述表单与数据表之间的绑定关系
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableBindingDTO {
    private Long bindingId;
    /** Physical or relation-bound table id (dw_table_definitions.id for SUB/PRIMARY). */
    private Long tableId;
    private String bindingType;    // "PRIMARY", "SUB", "RELATED"
    private String bindingMode;    // "EDITABLE", "READONLY"
    /** When bindingType is SUB: FULL (default) or FORM_ONLY (inline / link-form detail only, not standalone list). */
    private String subMode;
    private String foreignKeyField;
    private Integer sortOrder;
    private String tableName;
    private String tableType;
    private String tableDescription;
    /**
     * Primary-key column names from dw_field_definitions (is_primary_key), ordered by sort_order.
     * Used by portals to merge / identify sub-table rows without hardcoding {@code id}/{@code rowId}.
     */
    private List<String> primaryKeyFields;
}
