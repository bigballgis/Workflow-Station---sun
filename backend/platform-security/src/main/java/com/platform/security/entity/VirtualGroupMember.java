package com.platform.security.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Virtual Group Member entity.
 * Records the association between users and virtual groups.
 * Virtual groups are shared across services and users can obtain role permissions through virtual groups.
 * 
 * Architecture: User → Virtual Group → Role
 */
@Entity
@Table(name = "sys_virtual_group_members",
       uniqueConstraints = @UniqueConstraint(name = "uk_vg_member_group_user", columnNames = {"group_id", "user_id"}),
       indexes = {
           @Index(name = "idx_vg_member_group", columnList = "group_id"),
           @Index(name = "idx_vg_member_user", columnList = "user_id")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class VirtualGroupMember {
    
    @Id
    @Column(length = 64)
    private String id;
    
    @Column(name = "group_id", nullable = false, length = 64)
    private String groupId;
    
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;
    
    @CreationTimestamp
    @Column(name = "joined_at")
    private LocalDateTime joinedAt;
    
    @Column(name = "added_by", length = 64)
    private String addedBy;
}
