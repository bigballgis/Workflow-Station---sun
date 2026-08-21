package com.platform.common.dto;

import com.platform.common.enums.RelationTableStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * Shared DTO for Relation Table definition data.
 * Used across Admin Center, Developer Workstation, and User Portal.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelationTableDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Table definition ID
     */
    private Long id;

    /**
     * Physical table name (unique)
     */
    private String tableName;

    /**
     * Display name for UI
     */
    private String displayName;

    /**
     * Table description
     */
    private String description;

    /**
     * Table status: DRAFT, DEPLOYED, ROLLBACK
     */
    private RelationTableStatus status;

    /**
     * Whether the table is enabled
     */
    private Boolean enabled;

    /**
     * Whether the table is visible in User Portal
     */
    private Boolean portalVisible;

    /**
     * Current deployed version number
     */
    private Integer currentVersion;

    /**
     * Optional Function Unit grouping (sys_function_units.id); null = ungrouped.
     */
    private String functionUnitId;

    /**
     * Function Unit code, resolved for display; null = ungrouped.
     */
    private String functionUnitCode;

    /**
     * Function Unit name, resolved for display; null = ungrouped.
     */
    private String functionUnitName;

    /**
     * Field definitions of this table
     */
    private List<RelationFieldDTO> fieldDefinitions;

    /**
     * Permission level the current viewer holds on this table: READONLY | READ_WRITE.
     * Resolved per request against the caller's active role; null when not applicable.
     * @see com.platform.common.enums.RelationPermissionLevel
     */
    private String permissionLevel;

    /**
     * Record creation timestamp
     */
    private Instant createdAt;

    /**
     * Creator user ID
     */
    private String createdBy;

    /**
     * Last update timestamp
     */
    private Instant updatedAt;

    /**
     * Last updater user ID
     */
    private String updatedBy;
}
