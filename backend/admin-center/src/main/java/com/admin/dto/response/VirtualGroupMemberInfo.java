package com.admin.dto.response;

import com.admin.entity.VirtualGroupMember;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 虚拟组成员信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VirtualGroupMemberInfo {
    
    private String id;
    private String groupId;
    private String userId;
    private String username;
    private String fullName;
    private String employeeId;
    private String email;
    private String businessUnitId;
    private String businessUnitName;
    private String role;
    private Instant joinedAt;
    
    public static VirtualGroupMemberInfo fromEntity(VirtualGroupMember member) {
        VirtualGroupMemberInfo info = VirtualGroupMemberInfo.builder()
                .id(member.getId())
                .groupId(member.getVirtualGroup() != null ? member.getVirtualGroup().getId() : null)
                .userId(member.getUserId())
                .role("MEMBER") // 默认为成员，暂无角色字段
                .joinedAt(member.getJoinedAt())
                .build();
        
        if (member.getUser() != null) {
            info.setUsername(member.getUser().getUsername());
            info.setFullName(member.getUser().getFullName());
            info.setEmployeeId(member.getUser().getEmployeeId());
            info.setEmail(member.getUser().getEmail());
            info.setBusinessUnitId(member.getUser().getBusinessUnitId());
        }
        
        return info;
    }
}
