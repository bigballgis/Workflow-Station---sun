package com.developer.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

/**
 * Represents a single validation error with field and message information.
 * 
 * Requirements: 3.1
 */
@Getter
@AllArgsConstructor
public class ValidationError {
    
    @NonNull
    private final String field;
    
    @NonNull
    private final String message;
    
    private final String code;
    private final Object rejectedValue;
    
    public ValidationError(String field, String message) {
        this(field, message, null, null);
    }
    
    public ValidationError(String field, String message, String code) {
        this(field, message, code, null);
    }
    
    @Override
    public String toString() {
        return String.format("ValidationError{field='%s', message='%s', code='%s'}", 
                field, message, code);
    }
}