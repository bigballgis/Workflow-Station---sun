package com.developer.validation;

import com.developer.dto.ValidationResult;

/**
 * Generic input validator interface for validating and sanitizing input data.
 * This interface provides a contract for implementing type-specific validation logic.
 * 
 * @param <T> The type of input to validate
 */
public interface InputValidator<T> {
    
    /**
     * Validates the input and returns a detailed validation result.
     * 
     * @param input The input to validate
     * @return ValidationResult containing validation status and any errors/warnings
     */
    ValidationResult validate(T input);
    
    /**
     * Sanitizes the input by removing or escaping potentially dangerous content.
     * This method should be called after validation to ensure safe input processing.
     * 
     * @param input The input to sanitize
     * @return The sanitized input
     */
    T sanitize(T input);
    
    /**
     * Quick validation check that returns only a boolean result.
     * Use this for simple validation scenarios where detailed error information is not needed.
     * 
     * @param input The input to validate
     * @return true if the input is valid, false otherwise
     */
    boolean isValid(T input);
    
    /**
     * Gets the name of this validator for logging and debugging purposes.
     * 
     * @return The validator name
     */
    default String getValidatorName() {
        return this.getClass().getSimpleName();
    }
}