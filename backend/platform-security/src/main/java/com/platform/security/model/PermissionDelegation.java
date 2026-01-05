package com.platform.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Permission Delegation model for temporary permission transfer.
 * Validates: Requirements 4.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionDelegation implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String id;
    private String delegatorId;
    private String delegatorName;
    private String delegateeId;
    private String delegateeName;
    private Set<String> delegatedPermissions;
    private Set<String> delegatedRoles;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String reason;
    private DelegationStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    
    /**
     * Check if the delegation is currently active.
     */
    public boolean isActive() {
        if (status != DelegationStatus.ACTIVE) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(startTime) && !now.isAfter(endTime);
    }
    
    /**
     * Check if the delegation has expired.
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(endTime);
    }
    
    /**
     * Check if the delegation is pending (not yet started).
     */
    public boolean isPending() {
        return status == DelegationStatus.ACTIVE && LocalDateTime.now().isBefore(startTime);
    }
    
    public enum DelegationStatus {
        ACTIVE,
        REVOKED,
        EXPIRED
    }
}
