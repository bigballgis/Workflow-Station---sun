package com.admin.entity;

import com.admin.enums.VirtualGroupType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * 虚拟组实体
 * 支持项目组、工作组、临时组和任务处理组等多种虚拟组类型
 * 虚拟组是跨服务共享的，用户可以通过虚拟组获得角色权限
 */
@Entity
@Table(name = "sys_virtual_groups")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class VirtualGroup {
    
    @Id
    @Column(length = 64)
    private String id;
    
    @Column(name = "name", nullable = false, length = 100)
    private String name;
    
    @Column(name = "code", nullable = false, length = 50, unique = true)
    private String code;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private VirtualGroupType type;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    /**
     * AD Group 名称，用于与 Active Directory 系统集成
     */
    @Column(name = "ad_group", length = 100)
    private String adGroup;
    
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";
    
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
    
    @CreatedBy
    @Column(name = "created_by", length = 64, updatable = false)
    private String createdBy;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @LastModifiedBy
    @Column(name = "updated_by", length = 64)
    private String updatedBy;
    
    @JsonIgnore
    @OneToMany(mappedBy = "virtualGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<VirtualGroupMember> members = new HashSet<>();
    
    /**
     * 检查虚拟组是否有效
     */
    public boolean isValid() {
        return "ACTIVE".equals(status);
    }
    
    /**
     * 检查虚拟组是否是系统内置组
     */
    public boolean isSystem() {
        return type == VirtualGroupType.SYSTEM;
    }
    
    /**
     * 获取成员数量
     */
    public int getMemberCount() {
        return members != null ? members.size() : 0;
    }
    
    /**
     * 添加成员
     */
    public void addMember(VirtualGroupMember member) {
        if (members == null) {
            members = new HashSet<>();
        }
        member.setVirtualGroup(this);
        members.add(member);
    }
    
    /**
     * 移除成员
     */
    public void removeMember(VirtualGroupMember member) {
        if (members != null) {
            members.remove(member);
            member.setVirtualGroup(null);
        }
    }
}
