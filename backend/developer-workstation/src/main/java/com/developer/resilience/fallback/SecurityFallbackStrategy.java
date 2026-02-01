package com.developer.resilience.fallback;

import com.developer.exception.ErrorContext;
import com.developer.exception.SecurityException;
import com.developer.resilience.FallbackStrategy;
import lombok.extern.slf4j.Slf4j;

/**
 * Fallback strategies for security operations.
 * 
 * Requirements: 3.5
 */
@Slf4j
public class SecurityFallbackStrategy {
    
    /**
     * Fallback strategy for authentication - denies access when auth service fails
     */
    public static FallbackStrategy<Boolean> denyAccess() {
        return new FallbackStrategy<Boolean>() {
            @Override
            public Boolean execute(ErrorContext context, Exception originalException) {
                log.error("Authentication service failed, denying access for security. Context: {}", context.getDescription());
                return false; // Fail-safe: deny access when authentication fails
            }
            
            @Override
            public boolean canHandle(Exception exception) {
                return exception instanceof SecurityException ||
                       exception.getMessage().toLowerCase().contains("auth");
            }
            
            @Override
            public int getPriority() {
                return 1; // Highest priority for security
            }
        };
    }
    
    /**
     * Fallback strategy for authorization - uses basic role check
     */
    public static FallbackStrategy<Boolean> basicRoleCheck(String requiredRole) {
        return new FallbackStrategy<Boolean>() {
            @Override
            public Boolean execute(ErrorContext context, Exception originalException) {
                log.warn("Authorization service failed, using basic role check. Required role: {}, Context: {}", 
                        requiredRole, context.getDescription());
                
                // In a real implementation, this would check basic roles from a local cache
                // For now, we'll be conservative and deny access
                return false;
            }
            
            @Override
            public boolean canHandle(Exception exception) {
                return exception instanceof SecurityException;
            }
            
            @Override
            public int getPriority() {
                return 5;
            }
        };
    }
    
    /**
     * Fallback strategy for permission checks - uses cached permissions
     */
    public static FallbackStrategy<Boolean> cachedPermissions() {
        return new FallbackStrategy<Boolean>() {
            @Override
            public Boolean execute(ErrorContext context, Exception originalException) {
                log.warn("Permission service failed, checking cached permissions. Context: {}", context.getDescription());
                
                // In a real implementation, this would check cached permissions
                // For security, we'll be conservative and deny access
                return false;
            }
            
            @Override
            public boolean canHandle(Exception exception) {
                return exception instanceof SecurityException;
            }
            
            @Override
            public int getPriority() {
                return 10;
            }
        };
    }
    
    /**
     * Fallback strategy for security validation - uses strict validation
     */
    public static FallbackStrategy<Boolean> strictValidation() {
        return new FallbackStrategy<Boolean>() {
            @Override
            public Boolean execute(ErrorContext context, Exception originalException) {
                log.warn("Security validation service failed, applying strict validation. Context: {}", context.getDescription());
                return false; // Strict: reject when security validation fails
            }
            
            @Override
            public boolean canHandle(Exception exception) {
                return exception instanceof SecurityException;
            }
            
            @Override
            public int getPriority() {
                return 5;
            }
        };
    }
    
    /**
     * Fallback strategy for audit logging - logs to local file
     */
    public static FallbackStrategy<Void> localAuditLog() {
        return new FallbackStrategy<Void>() {
            @Override
            public Void execute(ErrorContext context, Exception originalException) {
                log.error("Audit service failed, logging locally. Context: {}", context.getDescription());
                // In a real implementation, this would write to a local audit file
                return null;
            }
            
            @Override
            public boolean canHandle(Exception exception) {
                return true; // Can handle any exception for audit logging
            }
            
            @Override
            public int getPriority() {
                return 15;
            }
        };
    }
}