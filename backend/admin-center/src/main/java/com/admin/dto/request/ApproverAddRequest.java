package com.admin.dto.request;

import com.admin.enums.ApproverTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 添加审批人请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApproverAddRequest {
    
    @NotNull(message = "目标类型不能为空")
    private ApproverTargetType targetType;
    
    @NotBlank(message = "目标ID不能为空")
    private String targetId;
    
    @NotBlank(message = "用户ID不能为空")
    private String userId;
}
