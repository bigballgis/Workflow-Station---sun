package com.admin.dto.response;

import com.admin.entity.UserBusinessUnitRole;
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
    
    public static UserBusinessUnitRoleInfo fromEntity(UserBusinessUnitRole entity) {
        UserBusinessUnitRoleInfo info = UserBusinessUnitRoleInfo.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .businessUnitId(entity.getBusinessUnitId())
                .roleId(entity.getRoleId())
                .createdAt(entity.getCreatedAt())
                .build();
        
        if (entity.getUser() != null) {
            info.setUserName(entity.getUser().getUsername());
            info.setUserFullName(entity.getUser().getFullName());
        }
        
        if (entity.getBusinessUnit() != null) {
            info.setBusinessUnitName(entity.getBusinessUnit().getName());
        }
        
        if (entity.getRole() != null) {
            info.setRoleName(entity.getRole().getName());
            info.setRoleCode(entity.getRole().getCode());
        }
        
        return info;
    }
}
