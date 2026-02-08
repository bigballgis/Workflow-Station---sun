package com.developer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for individual version entry in version history.
 * Contains detailed information about a specific version.
 * 
 * Requirements: 3.3, 3.4, 3.5 - Version History Display
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionHistoryEntry {
    
    /**
     * The version number
     */
    private String version;
    
    /**
     * Whether this version is currently active
     */
    private Boolean isActive;
    
    /**
     * When this version was deployed
     */
    private Instant deployedAt;
    
    /**
     * Number of process instances bound to this version
     */
    private Long processInstanceCount;
    
    /**
     * Whether this version can be rolled back to
     * (false for active version, true for inactive versions)
     */
    private Boolean canRollback;
    
    /**
     * The ID of this version
     */
    private Long versionId;
}
