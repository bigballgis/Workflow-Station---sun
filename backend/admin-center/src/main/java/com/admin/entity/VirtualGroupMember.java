package com.admin.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * 虚拟组成员实体
 * 记录用户与虚拟组的关联关系
 * 虚拟组是跨服务共享的，用户可以通过虚拟组获得角色权限
 */
@Entity
@Table(name = "sys_virtual_group_members",
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
    
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private VirtualGroup virtualGroup;
    
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "joined_at")
    @Builder.Default
    private Instant joinedAt = Instant.now();
    
    /**
     * 获取用户ID
     */
    public String getUserId() {
        return user != null && user.getId() != null ? user.getId().toString() : null;
    }
    
    /**
     * 获取虚拟组ID
     */
    public String getGroupId() {
        return virtualGroup != null ? virtualGroup.getId() : null;
    }
}
