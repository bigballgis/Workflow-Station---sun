package com.admin.dto.request;

import com.platform.security.enums.AssignmentTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 创建角色分配请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAssignmentRequest {
    
    /**
     * 角色ID（从路径参数设置，不需要在请求体中提供）
     */
    private String roleId;
    
    @NotNull(message = "分配目标类型不能为空")
    private AssignmentTargetType targetType;
    
    @NotBlank(message = "分配目标ID不能为空")
    private String targetId;
    
    /**
     * 有效期开始时间（可选）
     */
    private LocalDateTime validFrom;
    
    /**
     * 有效期结束时间（可选）
     */
    private LocalDateTime validTo;
}
