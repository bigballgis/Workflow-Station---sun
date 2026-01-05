package com.platform.common.exception;

import com.platform.common.enums.ErrorCode;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Exception for validation failures.
 */
@Getter
public class ValidationException extends PlatformException {
    
    private final List<FieldError> fieldErrors;
    
    public ValidationException() {
        super(ErrorCode.VALIDATION_FAILED);
        this.fieldErrors = null;
    }
    
    public ValidationException(String message) {
        super(ErrorCode.VALIDATION_FAILED, message);
        this.fieldErrors = null;
    }
    
    public ValidationException(List<FieldError> fieldErrors) {
        super(ErrorCode.VALIDATION_FAILED, "Validation failed", 
              Map.of("fieldErrors", fieldErrors));
        this.fieldErrors = fieldErrors;
    }
    
    public ValidationException(String field, String message) {
        super(ErrorCode.VALIDATION_FAILED, message,
              Map.of("field", field));
        this.fieldErrors = List.of(new FieldError(field, message));
    }
    
    /**
     * Field-level validation error
     */
    public record FieldError(String field, String message) {}
}
