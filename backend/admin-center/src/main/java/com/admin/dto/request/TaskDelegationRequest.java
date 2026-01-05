package com.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务委托请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDelegationRequest {
    
    @NotBlank(message = "任务ID不能为空")
    private String taskId;
    
    @NotBlank(message = "目标用户ID不能为空")
    private String toUserId;
    
    private String reason;
    
    private String comment;
}
