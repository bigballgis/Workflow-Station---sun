package com.workflow.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色分配请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleAssignmentRequest {
    
    /**
     * 目标用户名
     */
    private String username;
    
    /**
     * 角色名称
     */
    private String role;
    
    /**
     * 操作者用户名
     */
    private String operator;
    
    /**
     * 操作原因
     */
    private String reason;
    
    /**
     * 有效期（天数，null表示永久）
     */
    private Integer validDays;
}
