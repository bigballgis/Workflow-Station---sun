package com.admin.entity;

import com.platform.common.enums.RelationDataType;
import jakarta.persistence.*;
import lombok.*;

/**
 * Relation Table 字段定义实体
 * 包含字段名、数据类型、长度、是否允许为空、是否主键、默认值、注释等
 */
@Entity
@Table(name = "rt_field_definitions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class RelationFieldDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id", nullable = false)
    private RelationTableDefinition tableDefinition;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 50)
    private RelationDataType dataType;

    @Column(name = "length")
    private Integer length;

    @Column(name = "precision_value")
    private Integer precision;

    @Column(name = "scale")
    private Integer scale;

    @Column(name = "nullable")
    @Builder.Default
    private Boolean nullable = true;

    @Column(name = "is_primary_key")
    @Builder.Default
    private Boolean isPrimaryKey = false;

    @Column(name = "default_value", length = 500)
    private String defaultValue;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
