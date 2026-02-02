package com.admin.service;

import com.admin.dto.request.LoginRequest;
import com.admin.dto.response.LoginResponse;

/**
 * 认证服务接口
 */
public interface AuthService {
    
    /**
     * 用户登录（需 SYS_ADMIN 或 AUDITOR 等 Admin Center 权限）
     */
    LoginResponse login(LoginRequest request, String ipAddress, String userAgent);

    /**
     * Developer Workstation 登录（仅校验用户名密码与状态，不要求 Admin Center 角色）
     */
    LoginResponse loginForDeveloper(LoginRequest request, String ipAddress, String userAgent);
    
    /**
     * 用户登出
     */
    void logout(String token);
    
    /**
     * 刷新令牌
     */
    LoginResponse.UserLoginInfo refreshToken(String refreshToken);
    
    /**
     * 获取当前用户信息
     */
    LoginResponse.UserLoginInfo getCurrentUser(String token);
    
    /**
     * 验证令牌
     */
    boolean validateToken(String token);
}
