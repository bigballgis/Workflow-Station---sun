package com.portal.dto;

import com.platform.security.enums.AssignmentTargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

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
        private String language;
    }
    
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
