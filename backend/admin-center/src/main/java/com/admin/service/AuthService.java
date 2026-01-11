package com.admin.service;

import com.admin.dto.request.LoginRequest;
import com.admin.dto.response.LoginResponse;

/**
 * 认证服务接口
 */
public interface AuthService {
    
    /**
     * 用户登录
     */
    LoginResponse login(LoginRequest request, String ipAddress, String userAgent);
    
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
