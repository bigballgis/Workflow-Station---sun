package com.platform.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Data Permission model for row-level and column-level security.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataPermission implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String id;
    private String resourceType;
    private String filterExpression;
    private Map<String, Object> filterParameters;
    private List<String> accessibleColumns;
    private List<String> excludedColumns;
    private boolean enabled;
    
    /**
     * Check if a column is accessible.
     */
    public boolean isColumnAccessible(String column) {
        if (!enabled) {
            return true;
        }
        if (excludedColumns != null && excludedColumns.contains(column)) {
            return false;
        }
        if (accessibleColumns != null && !accessibleColumns.isEmpty()) {
            return accessibleColumns.contains(column);
        }
        return true;
    }
}
