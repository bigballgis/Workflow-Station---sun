package com.workflow.exception;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Workflow validation exception.
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
     * Build a detailed error message from the validation error list.
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
     * Validation error detail.
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
