package com.admin.service;

import com.admin.dto.request.LoginRequest;
import com.admin.dto.response.LoginResponse;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 认证服务接口
 */
public interface AuthService {
    
    /**
     * 用户登录
     */
    LoginResponse login(LoginRequest request, String ipAddress, String userAgent, HttpServletResponse response);
    
    /**
     * 用户登出
     */
    void logout(String token);
    
    /**
     * 刷新会话：吊销旧 refresh token 并签发新的 access + refresh（轮换）。
     */
    LoginResponse refreshLogin(String refreshToken, HttpServletResponse response);
    
    /**
     * 获取当前用户信息
     */
    LoginResponse.UserLoginInfo getCurrentUser(String token);
    
    /**
     * 验证令牌
     */
    boolean validateToken(String token);

    /**
     * 修改当前用户密码；成功后会使当前 access token 失效，需重新登录。
     */
    void changePassword(String accessToken, String oldPassword, String newPassword);
    
    /**
     * 为用户信息生成新的 access token
     */
    String generateAccessTokenForUser(LoginResponse.UserLoginInfo userInfo);

    /**
     * 统一登录（SSO）已校验身份后，为本系统签发会话（须具备管理端访问角色）。
     */
    LoginResponse issueSsoSession(String userId, String ipAddress, String userAgent, HttpServletResponse response);
}
