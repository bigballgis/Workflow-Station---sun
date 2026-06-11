package com.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Table binding DTO.
 * Describes the binding relationship between a form and a data table.
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
    /**
     * Designer-configured display name (dw_table_definitions.table_display_name for SUB/PRIMARY,
     * rt_table_definitions.display_name for RELATED). Portals MUST prefer this over
     * {@link #tableName} when rendering sub-table headers / tab labels, so the User Portal
     * matches the Developer Workstation Form Preview (portal-design-parity).
     * May be {@code null} when the designer left it blank — callers should fall back to
     * {@link #tableName}.
     */
    private String tableDisplayName;
    private String tableType;
    private String tableDescription;
    /**
     * Primary-key column names from dw_field_definitions (is_primary_key), ordered by sort_order.
     * Used by portals to merge / identify sub-table rows without hardcoding {@code id}/{@code rowId}.
     */
    private List<String> primaryKeyFields;
    /**
     * When bindingType is SUB: {@code structuralFk} (default) or {@code miParticipantRow} for MI slice bindings.
     */
    private String bindingLinkMode;
    /**
     * Table field metadata (FK/PK) for Portal / Preview row-add runtime (PRD S5).
     */
    private List<TableFieldDefinitionDTO> fieldDefinitions;
}
