package com.developer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Result DTO for deployment operations.
 * 
 * Requirements: 7.1, 7.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentResult {
    
    private boolean success;
    private Long versionId;
    private String version;
    private String processDefinitionKey;
    private Instant deployedAt;
    private String error;
}
