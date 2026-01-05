package com.platform.common.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Context information for audit logging.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditContext {
    
    private String userId;
    private String username;
    private String ipAddress;
    private String userAgent;
    private String traceId;
    private String module;
}
