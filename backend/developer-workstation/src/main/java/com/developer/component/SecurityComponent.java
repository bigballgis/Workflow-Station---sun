package com.developer.component;

import java.util.Set;

/**
 * 安全组件接口
 */
public interface SecurityComponent {
    
    /**
     * 生成JWT令牌
     */
    String generateToken(String username, Set<String> roles);
    
    /**
     * 验证JWT令牌
     */
    boolean validateToken(String token);
    
    /**
     * 从令牌获取用户名
     */
    String getUsernameFromToken(String token);
    
    /**
     * 从令牌获取角色
     */
    Set<String> getRolesFromToken(String token);
    
    /**
     * 检查账户是否被锁定
     */
    boolean isAccountLocked(String username);
    
    /**
     * 记录登录失败
     */
    void recordLoginFailure(String username);
    
    /**
     * 重置登录失败计数
     */
    void resetLoginFailures(String username);
    
    /**
     * 检查用户是否有权限
     */
    boolean hasPermission(String username, String permission);
    
    /**
     * 检查用户是否有角色
     */
    boolean hasRole(String username, String role);
}
