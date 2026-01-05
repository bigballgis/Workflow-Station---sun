package com.platform.common.functionunit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Result of deployment rollback.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RollbackResult {
    
    private boolean success;
    private String deploymentId;
    private String previousVersion;
    private String restoredVersion;
    private LocalDateTime rolledBackAt;
    private String rolledBackBy;
    private String errorMessage;
}
