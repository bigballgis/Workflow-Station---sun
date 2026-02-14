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
    
    @NotNull(message = "{validation.target_type_required}")
    private ApproverTargetType targetType;
    
    @NotBlank(message = "{validation.target_id_required}")
    private String targetId;
    
    @NotBlank(message = "{validation.user_id_required}")
    private String userId;
}
