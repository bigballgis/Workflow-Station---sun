package com.developer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Data transfer object representing the impact of a rollback operation.
 * Used to provide users with information about what will be deleted before confirming the rollback.
 * 
 * Requirements: 6.3, 6.4, 6.5
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RollbackImpact {
    
    /**
     * The target version to rollback to
     */
    private String targetVersion;
    
    /**
     * The ID of the target version
     */
    private Long targetVersionId;
    
    /**
     * List of version numbers that will be deleted (all versions > target version)
     */
    private List<String> versionsToDelete;
    
    /**
     * List of version IDs that will be deleted
     */
    private List<Long> versionIdsToDelete;
    
    /**
     * Total number of process instances that will be deleted
     */
    private long totalProcessInstancesToDelete;
    
    /**
     * Warning message about the destructive nature of the operation
     */
    private String warningMessage;
    
    /**
     * Whether the rollback can proceed (false if target version is already active or doesn't exist)
     */
    private boolean canProceed;
    
    /**
     * Error message if rollback cannot proceed
     */
    private String errorMessage;
}
