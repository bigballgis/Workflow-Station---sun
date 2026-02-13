package com.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量角色成员操作请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchRoleMemberRequest {
    
    /**
     * 角色ID
     */
    @NotBlank(message = "{validation.role_id_required}")
    private String roleId;
    
    /**
     * 用户ID列表
     */
    @NotEmpty(message = "{validation.user_ids_required}")
    private List<String> userIds;
    
    /**
     * 操作类型: ADD, REMOVE
     */
    @NotBlank(message = "{validation.operation_type_required}")
    private String operationType;
    
    /**
     * 操作原因
     */
    private String reason;
}
