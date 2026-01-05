package com.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 部门角色用户信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentRoleUserInfo {
    
    private String userId;
    private String username;
    private String fullName;
    private String departmentId;
    private String departmentName;
    private String roleId;
    private String roleName;
    private String roleCode;
    private boolean active;
}
