package com.platform.security.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Business Unit Role Binding entity.
 * Binds roles to business units.
 */
@Entity
@Table(name = "sys_business_unit_roles", 
       uniqueConstraints = @UniqueConstraint(name = "uk_bu_role", columnNames = {"business_unit_id", "role_id"}),
       indexes = {
           @Index(name = "idx_bu_role_bu", columnList = "business_unit_id"),
           @Index(name = "idx_bu_role_role", columnList = "role_id")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class BusinessUnitRole {
    
    @Id
    @Column(length = 64)
    private String id;
    
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
