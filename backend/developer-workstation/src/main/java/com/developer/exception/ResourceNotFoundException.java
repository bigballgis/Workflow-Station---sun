package com.developer.exception;

import lombok.Getter;

/**
 * Exception for resource not found errors.
 * 
 * Requirements: 3.1, 3.4
 */
@Getter
public class ResourceNotFoundException extends RuntimeException {
    
    private final String resourceType;
    private final String resourceId;
    private final String errorCode;
    private final ErrorContext context;
    
    public ResourceNotFoundException(String resourceType, Long id, ErrorContext context) {
        super(String.format("%s with id %d not found", resourceType, id));
        this.resourceType = resourceType;
        this.resourceId = String.valueOf(id);
        this.errorCode = "RES_NOT_FOUND";
        this.context = context;
    }
    
    public ResourceNotFoundException(String resourceType, String name, ErrorContext context) {
        super(String.format("%s with name '%s' not found", resourceType, name));
        this.resourceType = resourceType;
        this.resourceId = name;
        this.errorCode = "RES_NOT_FOUND";
        this.context = context;
    }
    
    // Backward compatibility constructors
    public ResourceNotFoundException(String resourceType, Long id) {
        this(resourceType, id, ErrorContext.of("resource_lookup", "ResourceService"));
    }
    
    public ResourceNotFoundException(String resourceType, String name) {
        this(resourceType, name, ErrorContext.of("resource_lookup", "ResourceService"));
    }
    
    public ErrorCategory getCategory() {
        return ErrorCategory.DATA_ACCESS;
    }
    
    public ErrorSeverity getSeverity() {
        return ErrorSeverity.WARN;
    }
}
