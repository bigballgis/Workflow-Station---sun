package com.admin.dto.response;

import com.admin.entity.User;
import com.admin.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

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
    private String phone;
    private String fullName;
    private String employeeId;
    private String departmentId;
    private String departmentName;
    private String position;
    private UserStatus status;
    private Instant lastLoginAt;
    private Instant createdAt;
    
    public static UserInfo fromEntity(User user) {
        return UserInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .fullName(user.getFullName())
                .employeeId(user.getEmployeeId())
                .departmentId(user.getDepartmentId())
                .position(user.getPosition())
                .status(user.getStatus())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
