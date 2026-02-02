package com.admin.dto.response;

import com.platform.security.entity.VirtualGroupMember;
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
    
    public static VirtualGroupMemberInfo fromEntity(VirtualGroupMember member, com.platform.security.entity.VirtualGroup virtualGroup, com.platform.security.entity.User user) {
        VirtualGroupMemberInfo info = VirtualGroupMemberInfo.builder()
                .id(member.getId())
                .groupId(virtualGroup != null ? virtualGroup.getId() : member.getGroupId())
                .userId(member.getUserId())
                .role("MEMBER") // 默认为成员，暂无角色字段
                .joinedAt(member.getJoinedAt() != null ? member.getJoinedAt().atZone(java.time.ZoneId.systemDefault()).toInstant() : null)
                .build();
        
        if (user != null) {
            info.setUsername(user.getUsername());
            info.setFullName(user.getFullName());
            info.setEmployeeId(user.getEmployeeId());
            info.setEmail(user.getEmail());
            // businessUnitId 需要通过关联表获取，在调用处设置
        }
        
        return info;
    }
}
