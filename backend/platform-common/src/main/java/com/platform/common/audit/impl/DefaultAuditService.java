package com.platform.common.audit.impl;

import com.platform.common.audit.AuditLog;
import com.platform.common.audit.AuditService;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Default implementation of AuditService.
 * This is a simple in-memory implementation for development and testing.
 * Production environments should replace this with a database-backed implementation.
 * 
 * Validates: Requirements 3.8, 4.8, 13.3
 */
@Slf4j
public class DefaultAuditService implements AuditService {
    
    private final ConcurrentMap<String, AuditLog> auditLogs = new ConcurrentHashMap<>();
    
    @Override
    public AuditLog log(AuditLog auditLog) {
        try {
            auditLogs.put(auditLog.getId(), auditLog);
            log.info("Audit log recorded: {} - {} - {} - {}", 
                    auditLog.getAction(), 
                    auditLog.getResourceType(), 
                    auditLog.getUserId(), 
                    auditLog.isSuccess() ? "SUCCESS" : "FAILURE");
            return auditLog;
        } catch (Exception e) {
            log.error("Failed to record audit log: {}", e.getMessage());
            throw new RuntimeException("Failed to record audit log", e);
        }
    }
    
    @Override
    public List<AuditLog> findByUserId(String userId, int limit) {
        return auditLogs.values().stream()
                .filter(log -> userId.equals(log.getUserId()))
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<AuditLog> findByResource(String resourceType, String resourceId, int limit) {
        return auditLogs.values().stream()
                .filter(log -> resourceType.equals(log.getResourceType()) && 
                              resourceId.equals(log.getResourceId()))
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<AuditLog> findByAction(String action, int limit) {
        return auditLogs.values().stream()
                .filter(log -> action.equals(log.getAction()))
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    @Override
    public Optional<AuditLog> findById(String id) {
        return Optional.ofNullable(auditLogs.get(id));
    }
    
    @Override
    public List<AuditLog> findByTraceId(String traceId) {
        return auditLogs.values().stream()
                .filter(log -> traceId.equals(log.getTraceId()))
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .collect(Collectors.toList());
    }
}