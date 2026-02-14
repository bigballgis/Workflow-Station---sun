package com.workflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 认证结果DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticationResult {
    
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 消息
     */
    private String message;
    
    /**
     * 访问令牌
     */
    private String accessToken;
    
    /**
     * 刷新令牌
     */
    private String refreshToken;
    
    /**
     * 令牌过期时间（毫秒）
     */
    private Long expiresIn;
    
    /**
     * 用户安全信息
     */
    private UserSecurityInfo userInfo;
    
    /**
     * 认证时间
     */
    private LocalDateTime authTime;
    
    /**
     * 创建成功结果
     */
    public static AuthenticationResult success(String accessToken, String refreshToken, 
            long expiresIn, UserSecurityInfo userInfo) {
        return AuthenticationResult.builder()
                .success(true)
                .message("Authentication successful")
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(expiresIn)
                .userInfo(userInfo)
                .authTime(LocalDateTime.now())
                .build();
    }
    
    /**
     * 创建失败结果
     */
    public static AuthenticationResult failure(String message) {
        return AuthenticationResult.builder()
                .success(false)
                .message(message)
                .authTime(LocalDateTime.now())
                .build();
    }
}
