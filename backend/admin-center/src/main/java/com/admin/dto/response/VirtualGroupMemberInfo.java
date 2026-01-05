package com.admin.dto.response;

import com.admin.entity.VirtualGroupMember;
import com.admin.enums.VirtualGroupMemberRole;
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
    private String userId;
    private String username;
    private String fullName;
    private String email;
    private VirtualGroupMemberRole role;
    private Instant joinedAt;
    
    public static VirtualGroupMemberInfo fromEntity(VirtualGroupMember member) {
        VirtualGroupMemberInfo info = VirtualGroupMemberInfo.builder()
                .id(member.getId())
                .userId(member.getUserId())
                .role(member.getRole())
                .joinedAt(member.getJoinedAt())
                .build();
        
        if (member.getUser() != null) {
            info.setUsername(member.getUser().getUsername());
            info.setFullName(member.getUser().getFullName());
            info.setEmail(member.getUser().getEmail());
        }
        
        return info;
    }
}
