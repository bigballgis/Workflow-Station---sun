package com.admin.bi.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RBAC 映射响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RbacMappingResponse {

    private String sysRoleId;
    private String sysRoleName;
    private String sysRoleCode;
    private String sysRoleType;
    private List<SupersetRoleResponse> supersetRoles;
    private LocalDateTime lastUpdatedAt;
}
