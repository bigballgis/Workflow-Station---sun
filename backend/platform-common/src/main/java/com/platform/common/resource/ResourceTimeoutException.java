package com.platform.common.resource;

/**
 * Resource Timeout Exception
 * 
 * Thrown when a resource-intensive operation exceeds its configured timeout.
 * 
 * @author Platform Team
 * @version 1.0
 */
public class ResourceTimeoutException extends Exception {
    
    private final String operationId;
    private final long timeoutMs;
    
    public ResourceTimeoutException(String message) {
        super(message);
        this.operationId = null;
        this.timeoutMs = 0;
    }
    
    public ResourceTimeoutException(String message, String operationId, long timeoutMs) {
        super(message);
        this.operationId = operationId;
        this.timeoutMs = timeoutMs;
    }
    
    public ResourceTimeoutException(String message, Throwable cause) {
        super(message, cause);
        this.operationId = null;
        this.timeoutMs = 0;
    }
    
    public ResourceTimeoutException(String message, Throwable cause, String operationId, long timeoutMs) {
        super(message, cause);
        this.operationId = operationId;
        this.timeoutMs = timeoutMs;
    }
    
    public String getOperationId() {
        return operationId;
    }
    
    public long getTimeoutMs() {
        return timeoutMs;
    }
}