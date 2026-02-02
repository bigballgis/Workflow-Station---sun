package com.admin.dto.response;

import com.admin.entity.PermissionRequest;
import com.admin.enums.PermissionRequestStatus;
import com.admin.enums.PermissionRequestType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 权限申请信息DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionRequestInfo {
    
    private String id;
    private String applicantId;
    private String applicantName;
    private String applicantFullName;
    private PermissionRequestType requestType;
    private String targetId;
    private String targetName;
    private String roleIds;
    private String reason;
    private PermissionRequestStatus status;
    private String approverId;
    private String approverName;
    private String approverFullName;
    private String approverComment;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant approvedAt;
    
    public static PermissionRequestInfo fromEntity(PermissionRequest entity, 
                                                    com.platform.security.entity.User applicant,
                                                    com.platform.security.entity.User approver) {
        PermissionRequestInfo info = PermissionRequestInfo.builder()
                .id(entity.getId())
                .applicantId(entity.getApplicantId())
                .requestType(entity.getRequestType())
                .targetId(entity.getTargetId())
                .roleIds(entity.getRoleIds())
                .reason(entity.getReason())
                .status(entity.getStatus())
                .approverId(entity.getApproverId())
                .approverComment(entity.getApproverComment())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .approvedAt(entity.getApprovedAt())
                .build();
        
        if (applicant != null) {
            info.setApplicantName(applicant.getUsername());
            info.setApplicantFullName(applicant.getFullName());
        }
        
        if (approver != null) {
            info.setApproverName(approver.getUsername());
            info.setApproverFullName(approver.getFullName());
        }
        
        return info;
    }
}
