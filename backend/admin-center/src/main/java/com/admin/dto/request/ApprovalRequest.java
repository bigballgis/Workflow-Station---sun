package com.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Approval request DTO for permission request approvals.
 * Used for both approve and reject operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRequest {
    
    /**
     * The ID of the approver performing the action
     */
    @NotBlank(message = "审批人ID不能为空")
    private String approverId;
    
    /**
     * Comment or reason for the approval/rejection
     * Required for rejections, optional for approvals
     */
    private String comment;
}