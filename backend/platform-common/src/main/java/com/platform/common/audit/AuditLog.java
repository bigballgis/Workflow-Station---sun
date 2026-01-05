package com.platform.common.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Audit log entity for tracking user operations.
 * Validates: Requirements 3.8, 4.8, 13.3
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String id;
    private String userId;
    private String username;
    private String module;
    private String action;
    private String resourceType;
    private String resourceId;
    private Map<String, Object> requestData;
    private Map<String, Object> responseData;
    private Map<String, Object> changedFields;
    private String ipAddress;
    private String userAgent;
    private String traceId;
    private LocalDateTime timestamp;
    private Integer statusCode;
    private Long durationMs;
    private boolean success;
    private String errorMessage;
    
    /**
     * Check if this is a data modification operation.
     */
    public boolean isDataModification() {
        return action != null && (
                action.contains("CREATE") ||
                action.contains("UPDATE") ||
                action.contains("DELETE")
        );
    }
}
