package com.admin.dto.response;

import com.platform.security.entity.UserBusinessUnitRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 用户业务单元角色信息DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBusinessUnitRoleInfo {
    
    private String id;
    private String userId;
    private String userName;
    private String userFullName;
    private String businessUnitId;
    private String businessUnitName;
    private String roleId;
    private String roleName;
    private String roleCode;
    private Instant createdAt;
    
    public static UserBusinessUnitRoleInfo fromEntity(UserBusinessUnitRole entity,
                                                       com.platform.security.entity.User user,
                                                       com.platform.security.entity.BusinessUnit businessUnit,
                                                       com.platform.security.entity.Role role) {
        UserBusinessUnitRoleInfo info = UserBusinessUnitRoleInfo.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .businessUnitId(entity.getBusinessUnitId())
                .roleId(entity.getRoleId())
                .createdAt(entity.getCreatedAt() != null ? 
                    entity.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant() : null)
                .build();
        
        if (user != null) {
            info.setUserName(user.getUsername());
            info.setUserFullName(user.getFullName());
        }
        
        if (businessUnit != null) {
            info.setBusinessUnitName(businessUnit.getName());
        }
        
        if (role != null) {
            info.setRoleName(role.getName());
            info.setRoleCode(role.getCode());
        }
        
        return info;
    }
}
