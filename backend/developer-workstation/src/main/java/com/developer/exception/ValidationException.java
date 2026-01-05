package com.developer.exception;

import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 验证异常
 */
@Getter
public class ValidationException extends RuntimeException {
    
    private final List<Map<String, String>> errors;
    
    public ValidationException(String message, List<Map<String, String>> errors) {
        super(message);
        this.errors = errors;
    }
    
    public ValidationException(String field, String message) {
        super(message);
        this.errors = List.of(Map.of("field", field, "message", message));
    }
}
