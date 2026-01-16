package com.admin.dto.response;

import com.admin.entity.VirtualGroup;
import com.admin.enums.VirtualGroupType;
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
    
    public static VirtualGroupInfo fromEntity(VirtualGroup group) {
        return VirtualGroupInfo.builder()
                .id(group.getId())
                .name(group.getName())
                .code(group.getCode())
                .type(group.getType())
                .description(group.getDescription())
                .adGroup(group.getAdGroup())
                .status(group.getStatus())
                .memberCount(group.getMemberCount())
                .valid(group.isValid())
                .createdAt(group.getCreatedAt())
                .createdBy(group.getCreatedBy())
                .build();
    }
}
