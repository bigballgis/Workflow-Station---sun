package com.platform.common.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Platform-wide audit log data structure.
 * This is a simplified version for cross-module audit logging.
 * Validates: Requirements 3.8, 4.8, 13.3
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {
    
    private String id;
    private String userId;
    private String username;
    private String action;
    private String resourceType;
    private String resourceId;
    private String module;
    private LocalDateTime timestamp;
    private String ipAddress;
    private String userAgent;
    private String traceId;
    private boolean success;
    private String errorMessage;
    private long durationMs;
    private int statusCode;
    private Map<String, Object> requestData;
    private Map<String, Object> responseData;
    
    /**
     * Determines if this audit log represents a data modification operation.
     * Data modification operations include CREATE, UPDATE, DELETE actions.
     * 
     * @return true if the action represents a data modification, false otherwise
     */
    public boolean isDataModification() {
        if (action == null) {
            return false;
        }
        String upperAction = action.toUpperCase();
        return upperAction.contains("CREATE") || 
               upperAction.contains("UPDATE") || 
               upperAction.contains("DELETE") ||
               upperAction.contains("MODIFY") ||
               upperAction.contains("INSERT") ||
               upperAction.contains("REMOVE");
    }
}