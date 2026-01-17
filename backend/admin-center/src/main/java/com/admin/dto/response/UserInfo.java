package com.admin.dto.response;

import com.admin.entity.User;
import com.admin.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户信息DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfo {
    
    private String id;
    private String username;
    private String email;
    private String displayName;
    private String fullName;
    private String employeeId;
    private String businessUnitId;
    private String businessUnitName;
    private String position;
    private String entityManagerId;
    private String entityManagerName;
    private String functionManagerId;
    private String functionManagerName;
    private UserStatus status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    
    public static UserInfo fromEntity(User user) {
        return UserInfo.builder()
                .id(user.getId() != null ? user.getId().toString() : null)
                .username(user.getUsername())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .fullName(user.getFullName())
                .employeeId(user.getEmployeeId())
                // businessUnitId 需要通过关联表获取，在调用处设置
                .position(user.getPosition())
                .entityManagerId(user.getEntityManagerId() != null ? user.getEntityManagerId().toString() : null)
                .functionManagerId(user.getFunctionManagerId() != null ? user.getFunctionManagerId().toString() : null)
                .status(user.getStatus())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
