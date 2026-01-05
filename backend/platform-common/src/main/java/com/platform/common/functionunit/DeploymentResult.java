package com.platform.common.functionunit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Result of function unit deployment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentResult {
    
    private boolean success;
    private String deploymentId;
    private String functionUnitId;
    private String version;
    private Environment environment;
    private DeploymentStatus status;
    private LocalDateTime deployedAt;
    private String deployedBy;
    private String errorMessage;
}
