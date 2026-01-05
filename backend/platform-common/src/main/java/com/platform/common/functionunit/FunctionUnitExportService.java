package com.platform.common.functionunit;

/**
 * Service interface for exporting function units.
 * Validates: Requirements 2.6
 */
public interface FunctionUnitExportService {
    
    /**
     * Export a function unit as a ZIP package.
     * 
     * @param functionUnitId Function unit ID
     * @return ZIP package bytes
     */
    byte[] exportFunctionUnit(String functionUnitId);
    
    /**
     * Validate function unit completeness before export.
     * 
     * @param functionUnitId Function unit ID
     * @return Validation result
     */
    ValidationResult validateFunctionUnit(String functionUnitId);
}
