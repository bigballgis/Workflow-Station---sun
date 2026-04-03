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

    /** 例如 WORKSPACE_CONTEXT_REQUIRED：需前端二次选择 UBR 后再登录 */
    private String loginErrorCode;

    /** 与 {@link #loginErrorCode} 配套返回的可选工作台上下文列表 */
    private List<WorkspaceContextOption> workspaceContexts;

    /** 登录失败时的可读说明（成功时通常为空） */
    private String message;
    
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

        /** 当前工作台业务单元（来自 UBR）；无 UBR 时为 null */
        private String activeBusinessUnitId;
        private String activeBusinessUnitName;
        /** 当前工作台 BU 绑定角色主键 */
        private String activeRoleId;
        private String activeRoleName;
        /** UBR 条数大于 1 时前端可展示切换器 */
        private Boolean workspaceSwitcherVisible;

        /**
         * FULL：完整门户；PERMISSION_SELF_SERVICE_ONLY：无 UBR（|C|=0），仅权限自助等白名单能力
         */
        private String portalAccessMode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkspaceContextOption {
        private String businessUnitId;
        private String roleId;
        private String businessUnitName;
        private String roleCode;
        private String roleName;
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
