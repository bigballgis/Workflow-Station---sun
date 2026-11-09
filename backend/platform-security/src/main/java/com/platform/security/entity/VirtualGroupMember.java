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
       uniqueConstraints = @UniqueConstraint(name = "uk_vg_member_list_source",
               columnNames = {"group_id", "user_id", "list_type", "source"}),
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

    public static final String LIST_MEMBER = "MEMBER";
    public static final String LIST_TECH_LEAD = "TECH_LEAD";
    public static final String LIST_DEVELOPER = "DEVELOPER";
    public static final String SOURCE_AD = "AD";
    public static final String SOURCE_MANUAL = "MANUAL";

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

    /**
     * MEMBER for SYSTEM / CUSTOM groups; for a team (DEVELOPER group) the list the row is on,
     * named by the role it grants: TECH_LEAD or DEVELOPER.
     */
    @Column(name = "list_type", nullable = false, length = 20)
    @Builder.Default
    private String listType = LIST_MEMBER;

    /** AD (written by the AD sync) or MANUAL; one user may hold one row of each in the same list. */
    @Column(name = "source", nullable = false, length = 20)
    @Builder.Default
    private String source = SOURCE_MANUAL;
}
