package com.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务认领请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskClaimRequest {
    
    @NotBlank(message = "任务ID不能为空")
    private String taskId;
    
    @NotBlank(message = "虚拟组ID不能为空")
    private String groupId;
    
    private String comment;
}
