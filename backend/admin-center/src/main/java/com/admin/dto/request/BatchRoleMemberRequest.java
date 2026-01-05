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
    @NotBlank(message = "角色ID不能为空")
    private String roleId;
    
    /**
     * 用户ID列表
     */
    @NotEmpty(message = "用户ID列表不能为空")
    private List<String> userIds;
    
    /**
     * 操作类型: ADD, REMOVE
     */
    @NotBlank(message = "操作类型不能为空")
    private String operationType;
    
    /**
     * 操作原因
     */
    private String reason;
}
