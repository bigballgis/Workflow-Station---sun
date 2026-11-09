package com.platform.security.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Virtual Group entity for grouping users and assigning roles.
 * Supports project groups, work groups, temporary groups, and task processing groups.
 * Virtual groups are shared across services and users can obtain role permissions through virtual groups.
 * 
 * Architecture: User → Virtual Group → Role
 */
@Entity
@Table(name = "sys_virtual_groups", indexes = {
    @Index(name = "idx_virtual_group_code", columnList = "code"),
    @Index(name = "idx_virtual_group_type", columnList = "type"),
    @Index(name = "idx_virtual_group_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class VirtualGroup {

    /** Id of Public, the reserved team without lists (platform access model §6.4). */
    public static final String PUBLIC_TEAM_ID = "vg-dev-public";

    /** Code of System Administrators, the SYSTEM group whose fixed binding grants SYS_ADMIN (§5.2). */
    public static final String SYSTEM_ADMINISTRATORS_CODE = "SYSTEM_ADMINISTRATORS";

    @Id
    @Column(length = 64)
    private String id;
    
    @Column(nullable = false, length = 100)
    private String name;
    
    @Column(nullable = false, unique = true, length = 50)
    private String code;
    
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String type = "CUSTOM";
    
    @Column(name = "display_name", columnDefinition = "TEXT")
    private String displayName;
    
    /**
     * Rule expression for dynamic group membership
     */
    @Column(name = "rule_expression", columnDefinition = "TEXT")
    private String ruleExpression;
    
    /**
     * AD group synced into the members (SYSTEM / CUSTOM) or into the Developer list (DEVELOPER team).
     */
    @Column(name = "ad_group", length = 100)
    private String adGroup;

    /**
     * DEVELOPER teams only: AD group synced into the Technical Lead list.
     */
    @Column(name = "tech_lead_ad_group", length = 100)
    private String techLeadAdGroup;
    
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "created_by", length = 64)
    private String createdBy;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "updated_by", length = 64)
    private String updatedBy;
    
    /**
     * Check if virtual group is active
     */
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
    
    /**
     * Check if virtual group is a system group
     */
    public boolean isSystem() {
        return "SYSTEM".equals(type);
    }

    /** Public, the reserved team without lists. */
    public boolean isPublicTeam() {
        return PUBLIC_TEAM_ID.equals(id);
    }

    /** System Administrators, which must always keep an active member. */
    public boolean isSystemAdministrators() {
        return "SYSTEM".equals(type) && SYSTEM_ADMINISTRATORS_CODE.equals(code);
    }
}
