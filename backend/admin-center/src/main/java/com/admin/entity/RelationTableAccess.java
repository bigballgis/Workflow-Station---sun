package com.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Relation Table 访问权限配置实体
 * 参照 FunctionUnitAccess 模式，通过 Business Role 控制 User Portal 中的数据可见性
 */
@Entity
@Table(name = "rt_table_access")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class RelationTableAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 64)
    private String id;

    @Column(name = "table_id", nullable = false)
    private Long tableId;

    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @Column(name = "target_id", nullable = false, length = 64)
    private String targetId;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", length = 64, updatable = false)
    private String createdBy;
}
