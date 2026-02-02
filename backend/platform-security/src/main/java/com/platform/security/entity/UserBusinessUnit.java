package com.platform.security.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * User Business Unit membership entity.
 * Represents that a user is a member of a business unit (without role information).
 * Users obtain roles by joining virtual groups, and activate BU-Bounded roles by joining business units.
 */
@Entity
@Table(name = "sys_user_business_units", 
       uniqueConstraints = @UniqueConstraint(name = "uk_user_bu", columnNames = {"user_id", "business_unit_id"}),
       indexes = {
           @Index(name = "idx_user_bu_user", columnList = "user_id"),
           @Index(name = "idx_user_bu_bu", columnList = "business_unit_id")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class UserBusinessUnit {
    
    @Id
    @Column(length = 64)
    private String id;
    
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;
    
    @Column(name = "business_unit_id", nullable = false, length = 64)
    private String businessUnitId;
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "created_by", length = 64)
    private String createdBy;
}
