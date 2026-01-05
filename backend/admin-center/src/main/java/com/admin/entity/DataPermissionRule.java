package com.admin.entity;

import com.admin.enums.DataPermissionType;
import com.admin.enums.DataScopeType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * 数据权限规则实体
 */
@Entity
@Table(name = "admin_data_permission_rules", indexes = {
        @Index(name = "idx_dp_rule_type", columnList = "permission_type"),
        @Index(name = "idx_dp_rule_target", columnList = "target_type, target_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class DataPermissionRule {
    
    @Id
    @Column(length = 36)
    private String id;
    
    /** 规则名称 */
    @Column(nullable = false, length = 100)
    private String name;
    
    /** 权限类型 */
    @Enumerated(EnumType.STRING)
    @Column(name = "permission_type", nullable = false, length = 20)
    private DataPermissionType permissionType;
    
    /** 目标类型(ROLE/DEPARTMENT/USER) */
    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;
    
    /** 目标ID(角色ID/部门ID/用户ID) */
    @Column(name = "target_id", nullable = false, length = 36)
    private String targetId;
    
    /** 资源类型(表名或实体名) */
    @Column(nullable = false, length = 100)
    private String resourceType;
    
    /** 数据范围 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DataScopeType dataScope;
    
    /** 自定义过滤条件(SQL片段) */
    @Column(columnDefinition = "TEXT")
    private String customFilter;
    
    /** 是否启用 */
    @Column
    @Builder.Default
    private Boolean enabled = true;
    
    /** 优先级(数字越小优先级越高) */
    @Column
    @Builder.Default
    private Integer priority = 100;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    
    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
