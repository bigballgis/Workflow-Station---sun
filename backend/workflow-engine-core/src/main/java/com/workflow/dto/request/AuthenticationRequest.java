package com.workflow.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 认证请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticationRequest {
    
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 密码
     */
    private String password;
    
    /**
     * 客户端IP地址
     */
    private String ipAddress;
    
    /**
     * 用户代理
     */
    private String userAgent;
    
    /**
     * 认证类型（LOCAL, LDAP, SSO）
     */
    private String authType;
    
    /**
     * SSO令牌（SSO认证时使用）
     */
    private String ssoToken;
    
    /**
     * 记住我
     */
    private Boolean rememberMe;
}
