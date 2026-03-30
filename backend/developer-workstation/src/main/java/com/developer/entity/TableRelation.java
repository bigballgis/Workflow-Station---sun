package com.developer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 表关系实体
 */
@Entity
@Table(name = "dw_table_relations")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class TableRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "function_unit_id", nullable = false)
    private FunctionUnit functionUnit;

    @Column(name = "source_table_id", nullable = false)
    private Long sourceTableId;

    @Column(name = "source_field_name", nullable = false, length = 100)
    private String sourceFieldName;

    @Column(name = "relation_type", nullable = false, length = 20)
    private String relationType;

    @Column(name = "target_table_id", nullable = false)
    private Long targetTableId;

    @Column(name = "target_field_name", nullable = false, length = 100)
    private String targetFieldName;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
