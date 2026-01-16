package com.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 虚拟组角色绑定实体
 * 每个虚拟组只能绑定一个角色（单角色绑定）
 */
@Entity
@Table(name = "sys_virtual_group_roles", 
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_virtual_group_role_vg", columnNames = {"virtual_group_id"})
       })
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class VirtualGroupRole {
    
    @Id
    @Column(length = 64)
    private String id;
    
    @Column(name = "virtual_group_id", nullable = false, length = 64)
    private String virtualGroupId;
    
    @Column(name = "role_id", nullable = false, length = 64)
    private String roleId;
    
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
    
    @CreatedBy
    @Column(name = "created_by", length = 64, updatable = false)
    private String createdBy;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "virtual_group_id", insertable = false, updatable = false)
    private VirtualGroup virtualGroup;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", insertable = false, updatable = false)
    private Role role;
}
