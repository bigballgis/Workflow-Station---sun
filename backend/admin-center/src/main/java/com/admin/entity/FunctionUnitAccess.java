package com.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 功能单元访问权限配置
 * 定义哪些目标（角色、用户、虚拟组等）可以使用某个功能单元
 */
@Entity
@Table(name = "sys_function_unit_access")
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
    
    /** 访问类型：DEVELOPER, USER */
    @Column(name = "access_type", nullable = false, length = 20)
    private String accessType;
    
    /** 目标类型：ROLE, USER, VIRTUAL_GROUP */
    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;
    
    /** 目标ID（角色ID、用户ID或虚拟组ID） */
    @Column(name = "target_id", nullable = false, length = 64)
    private String targetId;
    
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
    
    @CreatedBy
    @Column(name = "created_by", length = 64, updatable = false)
    private String createdBy;
}
