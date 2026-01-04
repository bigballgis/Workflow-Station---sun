package com.workflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 用户安全信息DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSecurityInfo {
    
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 显示名称
     */
    private String displayName;
    
    /**
     * 邮箱
     */
    private String email;
    
    /**
     * 用户角色
     */
    private Set<String> roles;
    
    /**
     * 用户权限
     */
    private Set<String> permissions;
    
    /**
     * 部门ID
     */
    private String departmentId;
    
    /**
     * 部门名称
     */
    private String departmentName;
    
    /**
     * 最后登录时间
     */
    private LocalDateTime lastLoginTime;
    
    /**
     * 最后登录IP
     */
    private String lastLoginIp;
    
    /**
     * 账户是否启用
     */
    private Boolean enabled;
    
    /**
     * 账户是否锁定
     */
    private Boolean locked;
    
    /**
     * 密码过期时间
     */
    private LocalDateTime passwordExpiresAt;
}
