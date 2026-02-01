package com.platform.common.resource;

/**
 * Resource Limit Exceeded Exception
 * 
 * Thrown when resource limits (such as maximum concurrent operations) are exceeded.
 * 
 * @author Platform Team
 * @version 1.0
 */
public class ResourceLimitExceededException extends Exception {
    
    private final int currentUsage;
    private final int maxLimit;
    
    public ResourceLimitExceededException(String message) {
        super(message);
        this.currentUsage = 0;
        this.maxLimit = 0;
    }
    
    public ResourceLimitExceededException(String message, int currentUsage, int maxLimit) {
        super(message);
        this.currentUsage = currentUsage;
        this.maxLimit = maxLimit;
    }
    
    public ResourceLimitExceededException(String message, Throwable cause) {
        super(message, cause);
        this.currentUsage = 0;
        this.maxLimit = 0;
    }
    
    public ResourceLimitExceededException(String message, Throwable cause, int currentUsage, int maxLimit) {
        super(message, cause);
        this.currentUsage = currentUsage;
        this.maxLimit = maxLimit;
    }
    
    public int getCurrentUsage() {
        return currentUsage;
    }
    
    public int getMaxLimit() {
        return maxLimit;
    }
}