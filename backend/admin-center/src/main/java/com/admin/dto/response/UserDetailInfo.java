package com.admin.dto.response;

import com.admin.entity.User;
import com.admin.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 用户详情DTO
 * Validates: Requirements 7.1, 7.2, 7.3
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDetailInfo {
    
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
    private Boolean mustChangePassword;
    private Instant passwordExpiredAt;
    private Instant lastLoginAt;
    private String lastLoginIp;
    private Instant createdAt;
    private String createdBy;
    private Instant updatedAt;
    private String updatedBy;
    
    /**
     * 用户角色列表
     */
    private Set<RoleInfo> roles;
    
    /**
     * 最近登录历史（最多10条）
     */
    private List<LoginHistoryInfo> loginHistory;
    
    /**
     * 角色信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleInfo {
        private String roleId;
        private String roleCode;
        private String roleName;
        private String description;
    }
    
    /**
     * 登录历史信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginHistoryInfo {
        private Instant loginTime;
        private String ipAddress;
        private String userAgent;
        private Boolean success;
        private String failureReason;
    }
    
    public static UserDetailInfo fromEntity(User user) {
        return UserDetailInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .fullName(user.getFullName())
                .employeeId(user.getEmployeeId())
                .departmentId(user.getDepartmentId())
                .position(user.getPosition())
                .status(user.getStatus())
                .mustChangePassword(user.getMustChangePassword())
                .passwordExpiredAt(user.getPasswordExpiredAt())
                .lastLoginAt(user.getLastLoginAt())
                .lastLoginIp(user.getLastLoginIp())
                .createdAt(user.getCreatedAt())
                .createdBy(user.getCreatedBy())
                .updatedAt(user.getUpdatedAt())
                .updatedBy(user.getUpdatedBy())
                .build();
    }
}
