package com.platform.common.functionunit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a dependency conflict during import.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DependencyConflict {
    
    private String componentType;
    private String componentId;
    private String componentName;
    private String existingVersion;
    private String importingVersion;
    private ConflictType conflictType;
    private String resolution;
    
    public enum ConflictType {
        VERSION_MISMATCH,
        NAME_COLLISION,
        SCHEMA_INCOMPATIBLE,
        DEPENDENCY_MISSING
    }
}
