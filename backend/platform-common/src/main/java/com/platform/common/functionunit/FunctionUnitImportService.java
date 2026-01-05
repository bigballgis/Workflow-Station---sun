package com.platform.common.functionunit;

import java.util.List;

/**
 * Service interface for importing function units.
 * Validates: Requirements 2.7
 */
public interface FunctionUnitImportService {
    
    /**
     * Import a function unit from a ZIP package.
     * 
     * @param packageData ZIP package bytes
     * @return Import result
     */
    ImportResult importFunctionUnit(byte[] packageData);
    
    /**
     * Validate a package before import.
     * 
     * @param packageData ZIP package bytes
     * @return Validation result
     */
    ValidationResult validatePackage(byte[] packageData);
    
    /**
     * Check for dependency conflicts.
     * 
     * @param packageData ZIP package bytes
     * @return List of conflicts
     */
    List<DependencyConflict> checkConflicts(byte[] packageData);
}
