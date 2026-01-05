package com.platform.common.functionunit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of function unit validation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {
    
    private boolean valid;
    @Builder.Default
    private List<String> errors = new ArrayList<>();
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
    
    public static ValidationResult success() {
        return ValidationResult.builder().valid(true).build();
    }
    
    public static ValidationResult failure(String error) {
        return ValidationResult.builder()
                .valid(false)
                .errors(List.of(error))
                .build();
    }
    
    public void addError(String error) {
        if (errors == null) errors = new ArrayList<>();
        errors.add(error);
        valid = false;
    }
    
    public void addWarning(String warning) {
        if (warnings == null) warnings = new ArrayList<>();
        warnings.add(warning);
    }
}
