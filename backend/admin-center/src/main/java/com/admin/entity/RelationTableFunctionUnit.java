package com.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Many-to-many link between a Relation Table and a Function Unit.
 * A table with no rows here is Common (visible/usable across all Function Units).
 */
@Entity
@Table(name = "rt_table_function_units",
        uniqueConstraints = @UniqueConstraint(name = "uk_rt_table_fu", columnNames = {"relation_table_id", "function_unit_id"}),
        indexes = {
                @Index(name = "idx_rt_table_fu_table", columnList = "relation_table_id"),
                @Index(name = "idx_rt_table_fu_fu", columnList = "function_unit_id")
        })
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class RelationTableFunctionUnit {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "relation_table_id", nullable = false)
    private Long relationTableId;

    @Column(name = "function_unit_id", nullable = false, length = 64)
    private String functionUnitId;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", length = 64, updatable = false)
    private String createdBy;
}
