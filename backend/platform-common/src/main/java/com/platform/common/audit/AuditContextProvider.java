package com.platform.common.audit;

/**
 * Interface for providing audit context.
 * Implementations should extract context from the current request/security context.
 */
public interface AuditContextProvider {
    
    /**
     * Get the current audit context.
     * 
     * @return AuditContext or null if not available
     */
    AuditContext getContext();
}
