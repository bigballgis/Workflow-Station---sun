package com.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 业务单元角色绑定实体
 */
@Entity
@Table(name = "sys_business_unit_roles", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"business_unit_id", "role_id"}))
@EntityListeners(AuditingEntityListener.class)
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
    
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
    
    @CreatedBy
    @Column(name = "created_by", length = 64, updatable = false)
    private String createdBy;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_unit_id", insertable = false, updatable = false)
    private BusinessUnit businessUnit;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", insertable = false, updatable = false)
    private Role role;
}
