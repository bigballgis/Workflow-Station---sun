package com.developer.exception;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

import java.time.Instant;
import java.util.Map;
import java.util.HashMap;

/**
 * Context information for errors, providing detailed information about
 * the operation, component, and parameters when an error occurs.
 * 
 * Requirements: 3.2, 3.3
 */
@Getter
@Builder
public class ErrorContext {
    
    @NonNull
    private final String operation;
    
    @NonNull
    private final String component;
    
    @Builder.Default
    private final Map<String, Object> parameters = new HashMap<>();
    
    @Builder.Default
    private final Instant timestamp = Instant.now();
    
    private final String userId;
    private final String sessionId;
    private final String requestId;
    private final String ipAddress;
    
    /**
     * Create a simple error context with operation and component
     */
    public static ErrorContext of(String operation, String component) {
        return ErrorContext.builder()
                .operation(operation)
                .component(component)
                .build();
    }
    
    /**
     * Create an error context with additional parameters
     */
    public static ErrorContext of(String operation, String component, Map<String, Object> parameters) {
        return ErrorContext.builder()
                .operation(operation)
                .component(component)
                .parameters(new HashMap<>(parameters))
                .build();
    }
    
    /**
     * Create an error context with user information
     */
    public static ErrorContext withUser(String operation, String component, String userId) {
        return ErrorContext.builder()
                .operation(operation)
                .component(component)
                .userId(userId)
                .build();
    }
    
    /**
     * Add a parameter to the context
     */
    public ErrorContext withParameter(String key, Object value) {
        Map<String, Object> newParams = new HashMap<>(this.parameters);
        newParams.put(key, value);
        
        return ErrorContext.builder()
                .operation(this.operation)
                .component(this.component)
                .parameters(newParams)
                .timestamp(this.timestamp)
                .userId(this.userId)
                .sessionId(this.sessionId)
                .requestId(this.requestId)
                .ipAddress(this.ipAddress)
                .build();
    }
    
    /**
     * Get a formatted description of the context
     */
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("Operation: ").append(operation);
        sb.append(", Component: ").append(component);
        
        if (userId != null) {
            sb.append(", User: ").append(userId);
        }
        
        if (!parameters.isEmpty()) {
            sb.append(", Parameters: ").append(parameters);
        }
        
        return sb.toString();
    }
}