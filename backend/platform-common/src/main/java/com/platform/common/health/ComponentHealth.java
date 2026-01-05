package com.platform.common.health;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Health information for a single component.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComponentHealth {
    
    private String name;
    private HealthStatus status;
    private String message;
    private Map<String, Object> details;
    private long responseTimeMs;
}
