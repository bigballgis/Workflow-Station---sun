package com.platform.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * Data filter condition for row-level and column-level security.
 * Used to generate dynamic SQL conditions based on user permissions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataFilter implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * SQL filter expression (e.g., "department_id = :deptId AND status = :status")
     */
    private String filterExpression;
    
    /**
     * Parameters for the filter expression
     */
    private Map<String, Object> parameters;
    
    /**
     * Whether to apply the filter (false means no filtering)
     */
    private boolean enabled;
    
    /**
     * Create an empty filter (no restrictions)
     */
    public static DataFilter noFilter() {
        return DataFilter.builder()
                .enabled(false)
                .build();
    }
    
    /**
     * Create a simple filter with expression and parameters
     */
    public static DataFilter of(String expression, Map<String, Object> params) {
        return DataFilter.builder()
                .filterExpression(expression)
                .parameters(params)
                .enabled(true)
                .build();
    }
    
    /**
     * Check if this filter has any restrictions
     */
    public boolean hasRestrictions() {
        return enabled && filterExpression != null && !filterExpression.isBlank();
    }
}
