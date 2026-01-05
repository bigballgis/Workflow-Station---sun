package com.developer.exception;

/**
 * 资源不存在异常
 */
public class ResourceNotFoundException extends RuntimeException {
    
    public ResourceNotFoundException(String resourceType, Long id) {
        super(String.format("%s with id %d not found", resourceType, id));
    }
    
    public ResourceNotFoundException(String resourceType, String name) {
        super(String.format("%s with name '%s' not found", resourceType, name));
    }
}
