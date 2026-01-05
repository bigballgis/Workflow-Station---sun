package com.admin.dto.request;

import com.admin.enums.DelegationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 权限委托请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionDelegationRequest {
    
    @NotBlank(message = "委托人ID不能为空")
    private String delegatorId;
    
    @NotBlank(message = "受委托人ID不能为空")
    private String delegateeId;
    
    @NotBlank(message = "权限ID不能为空")
    private String permissionId;
    
    @NotNull(message = "委托类型不能为空")
    private DelegationType delegationType;
    
    @NotNull(message = "生效时间不能为空")
    private Instant validFrom;
    
    private Instant validTo;
    
    private String reason;
    
    private String conditions;
}