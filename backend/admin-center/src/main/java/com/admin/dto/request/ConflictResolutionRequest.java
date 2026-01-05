package com.admin.dto.request;

import com.admin.enums.ConflictResolutionStrategy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 权限冲突解决请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConflictResolutionRequest {
    
    @NotBlank(message = "冲突ID不能为空")
    private String conflictId;
    
    @NotNull(message = "解决策略不能为空")
    private ConflictResolutionStrategy resolutionStrategy;
    
    private String resolutionResult;
    
    private String resolvedBy;
}