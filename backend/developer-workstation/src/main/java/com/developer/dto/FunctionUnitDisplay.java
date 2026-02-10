package com.developer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for displaying function units in the UI.
 * Shows consolidated view with active version information.
 * 
 * Requirements: 3.1, 3.2 - UI Display and Version History
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunctionUnitDisplay {
    
    /**
     * The name of the function unit
     */
    private String functionUnitName;
    
    /**
     * The current active version number
     */
    private String currentVersion;
    
    /**
     * When the current version was deployed
     */
    private Instant deployedAt;
    
    /**
     * Total number of versions for this function unit
     */
    private Integer versionCount;
    
    /**
     * The ID of the active version
     */
    private Long activeVersionId;
}
