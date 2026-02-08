package com.developer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Function unit access permission configuration for Developer Workstation.
 * Defines which targets (roles, users, virtual groups) can use a specific function unit version.
 * 
 * This entity is part of the versioned deployment system and maintains separate
 * permission records for each function unit version.
 */
@Entity
@Table(name = "dw_function_unit_access")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class FunctionUnitAccess {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * The function unit version this permission applies to
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "function_unit_id", nullable = false)
    private FunctionUnit functionUnit;
    
    /**
     * Access type: DEVELOPER, USER
     */
    @Column(name = "access_type", nullable = false, length = 20)
    private String accessType;
    
    /**
     * Target type: ROLE, USER, VIRTUAL_GROUP
     */
    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;
    
    /**
     * Target ID (role ID, user ID, or virtual group ID)
     */
    @Column(name = "target_id", nullable = false, length = 64)
    private String targetId;
    
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
    
    @CreatedBy
    @Column(name = "created_by", length = 64, updatable = false)
    private String createdBy;
}
