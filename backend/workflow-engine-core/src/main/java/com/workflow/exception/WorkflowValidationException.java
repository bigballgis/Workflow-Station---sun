package com.workflow.exception;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 工作流验证异常
 */
public class WorkflowValidationException extends RuntimeException {
    
    private final List<ValidationError> validationErrors;
    
    public WorkflowValidationException(List<ValidationError> validationErrors) {
        super(buildMessage(validationErrors));
        this.validationErrors = validationErrors;
    }
    
    public WorkflowValidationException(String message) {
        super(message);
        this.validationErrors = List.of(new ValidationError("general", message, null));
    }
    
    public WorkflowValidationException(String message, List<ValidationError> validationErrors) {
        super(message);
        this.validationErrors = validationErrors;
    }
    
    public List<ValidationError> getValidationErrors() {
        return validationErrors;
    }
    
    /**
     * 根据验证错误列表构建详细的错误消息
     */
    private static String buildMessage(List<ValidationError> validationErrors) {
        if (validationErrors == null || validationErrors.isEmpty()) {
            return "Validation failed";
        }
        
        if (validationErrors.size() == 1) {
            return validationErrors.get(0).getMessage();
        }
        
        return validationErrors.stream()
                .map(ValidationError::getMessage)
                .collect(Collectors.joining("; "));
    }
    
    /**
     * 验证错误详情
     */
    public static class ValidationError {
        private String field;
        private String message;
        private Object rejectedValue;
        
        public ValidationError(String field, String message, Object rejectedValue) {
            this.field = field;
            this.message = message;
            this.rejectedValue = rejectedValue;
        }
        
        // Getters
        public String getField() { return field; }
        public String getMessage() { return message; }
        public Object getRejectedValue() { return rejectedValue; }
    }
}