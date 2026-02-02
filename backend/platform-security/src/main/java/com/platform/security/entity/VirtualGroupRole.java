package com.platform.security.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Virtual Group Role Binding entity.
 * Each virtual group can only bind one role (single role binding).
 * 
 * Architecture: User → Virtual Group → Role
 */
@Entity
@Table(name = "sys_virtual_group_roles", 
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_vg_role_group", columnNames = {"virtual_group_id"})
       },
       indexes = {
           @Index(name = "idx_vg_role_group", columnList = "virtual_group_id"),
           @Index(name = "idx_vg_role_role", columnList = "role_id")
       })
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
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "created_by", length = 64)
    private String createdBy;
}
