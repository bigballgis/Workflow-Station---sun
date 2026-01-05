package com.developer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 验证结果DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {
    
    @Builder.Default
    private boolean valid = true;
    
    @Builder.Default
    private List<ValidationError> errors = new ArrayList<>();
    
    @Builder.Default
    private List<ValidationWarning> warnings = new ArrayList<>();
    
    public void addError(String code, String message, String elementId) {
        this.valid = false;
        this.errors.add(new ValidationError(code, message, elementId));
    }
    
    public void addWarning(String code, String message, String elementId) {
        this.warnings.add(new ValidationWarning(code, message, elementId));
    }
    
    @Data
    @AllArgsConstructor
    public static class ValidationError {
        private String code;
        private String message;
        private String elementId;
    }
    
    @Data
    @AllArgsConstructor
    public static class ValidationWarning {
        private String code;
        private String message;
        private String elementId;
    }
}
