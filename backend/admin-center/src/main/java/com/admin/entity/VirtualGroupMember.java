package com.admin.entity;

import com.admin.enums.VirtualGroupMemberRole;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * 虚拟组成员实体
 * 记录用户与虚拟组的关联关系及成员角色
 */
@Entity
@Table(name = "admin_virtual_group_members",
       uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "user_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class VirtualGroupMember {
    
    @Id
    @Column(length = 64)
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private VirtualGroup virtualGroup;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    @Builder.Default
    private VirtualGroupMemberRole role = VirtualGroupMemberRole.MEMBER;
    
    @Column(name = "joined_at")
    @Builder.Default
    private Instant joinedAt = Instant.now();
    
    /**
     * 检查是否是组长
     */
    public boolean isLeader() {
        return role == VirtualGroupMemberRole.LEADER;
    }
    
    /**
     * 检查是否是普通成员
     */
    public boolean isMember() {
        return role == VirtualGroupMemberRole.MEMBER;
    }
    
    /**
     * 获取用户ID
     */
    public String getUserId() {
        return user != null ? user.getId() : null;
    }
    
    /**
     * 获取虚拟组ID
     */
    public String getGroupId() {
        return virtualGroup != null ? virtualGroup.getId() : null;
    }
}
