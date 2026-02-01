package com.developer.exception;

import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Exception for input validation failures.
 * Provides detailed validation error information.
 * 
 * Requirements: 3.1, 3.4
 */
@Getter
public class ValidationException extends ApplicationException {
    
    private final List<ValidationError> validationErrors;
    
    public ValidationException(String message, List<ValidationError> validationErrors, ErrorContext context) {
        super("VAL_VALIDATION_FAILED", message, context);
        this.validationErrors = validationErrors;
    }
    
    public ValidationException(String field, String message, ErrorContext context) {
        super("VAL_FIELD_INVALID", message, context);
        this.validationErrors = List.of(new ValidationError(field, message));
    }
    
    @Override
    public ErrorCategory getCategory() {
        return ErrorCategory.VALIDATION;
    }
    
    @Override
    public ErrorSeverity getSeverity() {
        return ErrorSeverity.WARN;
    }
    
    /**
     * Get validation errors in the legacy format for backward compatibility
     */
    public List<Map<String, String>> getErrors() {
        return validationErrors.stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getMessage()
                ))
                .toList();
    }
}
