package com.developer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Data transfer object representing the result of a rollback operation.
 * 
 * Requirements: 6.2, 6.3, 6.4, 6.6, 6.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RollbackResult {
    
    /**
     * Whether the rollback operation was successful
     */
    private boolean success;
    
    /**
     * The version that was rolled back to
     */
    private String rolledBackToVersion;
    
    /**
     * The ID of the version that was rolled back to
     */
    private Long rolledBackToVersionId;
    
    /**
     * List of version numbers that were deleted
     */
    private List<String> deletedVersions;
    
    /**
     * Number of process instances that were deleted
     */
    private long deletedProcessCount;
    
    /**
     * Error message if the rollback failed
     */
    private String errorMessage;
    
    /**
     * Detailed error information for debugging
     */
    private String errorDetails;
}
