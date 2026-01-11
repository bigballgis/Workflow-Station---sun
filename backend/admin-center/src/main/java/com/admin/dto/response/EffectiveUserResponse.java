package com.admin.dto.response;

import com.platform.security.dto.RoleSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 有效用户响应（有效用户列表）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EffectiveUserResponse {
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 显示名称
     */
    private String displayName;
    
    /**
     * 员工编号
     */
    private String employeeId;
    
    /**
     * 部门ID
     */
    private String departmentId;
    
    /**
     * 部门名称
     */
    private String departmentName;
    
    /**
     * 邮箱
     */
    private String email;
    
    /**
     * 角色来源列表（可能有多个）
     */
    private List<RoleSource> sources;
}
