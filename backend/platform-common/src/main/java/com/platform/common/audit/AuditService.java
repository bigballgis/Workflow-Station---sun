package com.platform.common.audit;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for audit logging.
 * Validates: Requirements 3.8, 4.8, 13.3
 */
public interface AuditService {
    
    /**
     * Create an audit log entry.
     */
    AuditLog log(AuditLog auditLog);
    
    /**
     * Find audit logs by user ID.
     */
    List<AuditLog> findByUserId(String userId, int limit);
    
    /**
     * Find audit logs by resource.
     */
    List<AuditLog> findByResource(String resourceType, String resourceId, int limit);
    
    /**
     * Find audit logs by action.
     */
    List<AuditLog> findByAction(String action, int limit);
    
    /**
     * Find audit log by ID.
     */
    Optional<AuditLog> findById(String id);
    
    /**
     * Find audit logs by trace ID.
     */
    List<AuditLog> findByTraceId(String traceId);
}
