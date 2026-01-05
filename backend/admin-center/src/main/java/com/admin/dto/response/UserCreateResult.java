package com.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户创建结果DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCreateResult {
    
    private boolean success;
    private String userId;
    private String username;
    private String message;
    
    public static UserCreateResult success(String userId, String username) {
        return UserCreateResult.builder()
                .success(true)
                .userId(userId)
                .username(username)
                .message("用户创建成功")
                .build();
    }
    
    public static UserCreateResult failure(String message) {
        return UserCreateResult.builder()
                .success(false)
                .message(message)
                .build();
    }
}
