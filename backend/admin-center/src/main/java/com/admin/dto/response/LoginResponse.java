package com.admin.dto.response;

import com.platform.security.dto.RoleSource;
import com.platform.security.enums.AssignmentTargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 登录响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private UserLoginInfo user;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserLoginInfo {
        private String userId;
        private String username;
        private String displayName;
        private String email;
        private List<String> roles;
        private List<String> permissions;
        private List<RoleWithSource> rolesWithSources;
        private String departmentId;
        private String language;
    }
    
    /**
     * 角色及来源信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleWithSource {
        private String roleCode;
        private String roleName;
        private AssignmentTargetType sourceType;
        private String sourceId;
        private String sourceName;
    }
}
