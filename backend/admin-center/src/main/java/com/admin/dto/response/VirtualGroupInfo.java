package com.admin.dto.response;

import com.platform.security.entity.VirtualGroup;
import com.admin.enums.VirtualGroupType;
import com.admin.util.EntityTypeConverter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 虚拟组信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VirtualGroupInfo {
    
    private String id;
    private String name;
    private String code;
    private VirtualGroupType type;
    private String description;
    /**
     * AD Group 名称，用于与 Active Directory 系统集成
     */
    private String adGroup;
    private String techLeadAdGroup;
    private String status;
    private int memberCount;
    private boolean valid;
    private Instant createdAt;
    private String createdBy;
    
    // 绑定的角色信息
    private String boundRoleId;
    private String boundRoleName;
    private String boundRoleCode;
    private String boundRoleType;

    /** Public 团队：没有名单、不能配 AD 组、不能停用或删除（平台访问模型 §6.4）。 */
    private boolean publicTeam;
    /** System Administrators：SYS_ADMIN 的唯一来源，不能没有有效成员（§5.2、AC-12）。 */
    private boolean systemAdministrators;

    public static VirtualGroupInfo fromEntity(VirtualGroup group) {
        return VirtualGroupInfo.builder()
                .id(group.getId())
                .name(group.getName())
                .code(group.getCode())
                .type(EntityTypeConverter.toVirtualGroupType(group.getType()))
                .description(group.getDisplayName())
                .adGroup(group.getAdGroup())
                .techLeadAdGroup(group.getTechLeadAdGroup())
                .status(group.getStatus())
                .memberCount(0) // Will be set by helper service
                .valid(false) // Will be set by helper service
                .createdAt(group.getCreatedAt() != null ? group.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant() : null)
                .createdBy(group.getCreatedBy())
                .publicTeam(group.isPublicTeam())
                .systemAdministrators(group.isSystemAdministrators())
                .build();
    }
}
