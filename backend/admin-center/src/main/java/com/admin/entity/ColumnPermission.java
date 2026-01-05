package com.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * 列级权限实体
 */
@Entity
@Table(name = "admin_column_permissions", indexes = {
        @Index(name = "idx_col_perm_rule", columnList = "rule_id"),
        @Index(name = "idx_col_perm_column", columnList = "column_name")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class ColumnPermission {
    
    @Id
    @Column(length = 36)
    private String id;
    
    /** 关联的数据权限规则 */
    @Column(name = "rule_id", nullable = false, length = 36)
    private String ruleId;
    
    /** 列名 */
    @Column(name = "column_name", nullable = false, length = 100)
    private String columnName;
    
    /** 是否可见 */
    @Column
    @Builder.Default
    private Boolean visible = true;
    
    /** 是否脱敏 */
    @Column
    @Builder.Default
    private Boolean masked = false;
    
    /** 脱敏规则(如: phone, email, idcard, custom) */
    @Column(length = 50)
    private String maskType;
    
    /** 自定义脱敏表达式 */
    @Column(length = 200)
    private String maskExpression;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
