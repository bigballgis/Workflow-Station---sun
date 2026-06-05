package com.admin.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.platform.common.enums.RelationDataType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;

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
    @ToString.Exclude
    @JsonIgnore
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

    @Column(name = "display_name", columnDefinition = "TEXT")
    private String displayName;

    @Column(name = "is_foreign_key")
    @Builder.Default
    private Boolean isForeignKey = false;

    @Column(name = "ref_table_id")
    private Long refTableId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ref_primary_key_fields", columnDefinition = "jsonb")
    private List<String> refPrimaryKeyFields;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pk_generation_json", columnDefinition = "jsonb")
    private Map<String, Object> pkGenerationJson;

    @Column(name = "fk_display_mode", length = 20)
    @Builder.Default
    private String fkDisplayMode = "readonly";

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
