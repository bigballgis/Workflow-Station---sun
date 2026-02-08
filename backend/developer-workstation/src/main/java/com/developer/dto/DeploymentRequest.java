package com.developer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request DTO for deploying a new version of a function unit.
 * 
 * Requirements: 1.1, 7.1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentRequest {
    
    @NotBlank(message = "BPMN XML is required")
    private String bpmnXml;
    
    @NotNull(message = "Change type is required")
    @Pattern(regexp = "^(major|minor|patch)$", message = "Change type must be one of: major, minor, patch")
    private String changeType;
    
    private Map<String, Object> metadata;
}
