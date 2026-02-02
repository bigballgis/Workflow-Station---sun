package com.platform.security.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * User Business Unit Role assignment entity.
 * Assigns roles to users within specific business units.
 */
@Entity
@Table(name = "sys_user_business_unit_roles", 
       uniqueConstraints = @UniqueConstraint(name = "uk_user_bu_role", columnNames = {"user_id", "business_unit_id", "role_id"}),
       indexes = {
           @Index(name = "idx_ubr_user", columnList = "user_id"),
           @Index(name = "idx_ubr_bu", columnList = "business_unit_id"),
           @Index(name = "idx_ubr_role", columnList = "role_id")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class UserBusinessUnitRole {
    
    @Id
    @Column(length = 64)
    private String id;
    
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;
    
    @Column(name = "business_unit_id", nullable = false, length = 64)
    private String businessUnitId;
    
    @Column(name = "role_id", nullable = false, length = 64)
    private String roleId;
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "created_by", length = 64)
    private String createdBy;
}
