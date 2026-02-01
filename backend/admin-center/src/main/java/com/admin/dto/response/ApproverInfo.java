package com.admin.dto.response;

import com.admin.entity.Approver;
import com.admin.enums.ApproverTargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 审批人信息DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApproverInfo {
    
    private String id;
    private ApproverTargetType targetType;
    private String targetId;
    private String userId;
    private String userName;
    private String userFullName;
    private Instant createdAt;
    
    public static ApproverInfo fromEntity(Approver approver) {
        ApproverInfo info = ApproverInfo.builder()
                .id(approver.getId())
                .targetType(approver.getTargetType())
                .targetId(approver.getTargetId())
                .userId(approver.getUserId())
                .createdAt(approver.getCreatedAt())
                .build();
        
        if (approver.getUser() != null) {
            info.setUserName(approver.getUser().getUsername());
            info.setUserFullName(approver.getUser().getFullName());
        }
        
        return info;
    }
}
