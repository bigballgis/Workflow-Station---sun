package com.workflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Security audit result DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityAuditResult {
    
    /**
     * Report generation time
     */
    private LocalDateTime reportTime;
    
    /**
     * Statistics start time
     */
    private LocalDateTime startTime;
    
    /**
     * Statistics end time
     */
    private LocalDateTime endTime;
    
    /**
     * Security score (0-100)
     */
    private int securityScore;
    
    /**
     * Successful login count
     */
    private long successfulLogins;
    
    /**
     * Failed login count
     */
    private long failedLogins;
    
    /**
     * Role assignment count
     */
    private long roleAssignments;
    
    /**
     * Role revocation count
     */
    private long roleRevocations;
    
    /**
     * Suspicious activity list
     */
    private List<SuspiciousActivity> suspiciousActivities;
    
    /**
     * Active user count
     */
    private long activeUsers;
    
    /**
     * Locked account count
     */
    private long lockedAccounts;
    
    /**
     * Suspicious activity DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuspiciousActivity {
        
        /**
         * Activity type
         */
        private String type;
        
        /**
         * Description
         */
        private String description;
        
        /**
         * Severity (HIGH, MEDIUM, LOW)
         */
        private String severity;
        
        /**
         * Related username
         */
        private String username;
        
        /**
         * Related IP address
         */
        private String ipAddress;
        
        /**
         * Detection time
         */
        private LocalDateTime detectedTime;
        
        /**
         * Whether processed
         */
        private Boolean handled;
    }
}
