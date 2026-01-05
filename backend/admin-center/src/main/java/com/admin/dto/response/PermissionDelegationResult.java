package com.admin.dto.response;

import com.admin.entity.PermissionDelegation;
import com.admin.enums.DelegationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 权限委托结果DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionDelegationResult {
    
    private String id;
    private String delegatorId;
    private String delegatorName;
    private String delegateeId;
    private String delegateeName;
    private String permissionId;
    private String permissionName;
    private DelegationType delegationType;
    private Instant validFrom;
    private Instant validTo;
    private String reason;
    private String status;
    private Instant createdAt;
    private String createdBy;
    private boolean isValid;
    private boolean isExpired;
    
    public static PermissionDelegationResult fromEntity(PermissionDelegation delegation) {
        return PermissionDelegationResult.builder()
                .id(delegation.getId())
                .delegatorId(delegation.getDelegatorId())
                .delegateeId(delegation.getDelegateeId())
                .permissionId(delegation.getPermission().getId())
                .permissionName(delegation.getPermission().getName())
                .delegationType(delegation.getDelegationType())
                .validFrom(delegation.getValidFrom())
                .validTo(delegation.getValidTo())
                .reason(delegation.getReason())
                .status(delegation.getStatus())
                .createdAt(delegation.getCreatedAt())
                .createdBy(delegation.getCreatedBy())
                .isValid(delegation.isValid())
                .isExpired(delegation.isExpired())
                .build();
    }
    
    public static PermissionDelegationResult success(PermissionDelegation delegation) {
        return fromEntity(delegation);
    }
}