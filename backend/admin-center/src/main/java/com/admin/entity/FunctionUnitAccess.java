package com.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 功能单元访问权限配置
 * 定义哪些业务角色可以使用某个功能单元
 * 简化后只支持角色分配，不再支持部门、虚拟组等
 */
@Entity
@Table(name = "sys_function_unit_access", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"function_unit_id", "role_id"}))
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class FunctionUnitAccess {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 64)
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "function_unit_id", nullable = false)
    private FunctionUnit functionUnit;
    
    /** 业务角色ID */
    @Column(name = "role_id", nullable = false, length = 64)
    private String roleId;
    
    /** 角色名称（冗余字段，方便显示） */
    @Column(name = "role_name", length = 100)
    private String roleName;
    
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
    
    @CreatedBy
    @Column(name = "created_by", length = 64, updatable = false)
    private String createdBy;
}
