package com.developer.entity;

import com.developer.enums.DataType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

/**
 * 公共表字段定义实体
 */
@Entity
@Table(name = "dw_common_field_definitions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"common_table_id", "field_name"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class CommonFieldDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "common_table_id", nullable = false)
    private CommonTableDefinition commonTable;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 50)
    private DataType dataType;

    @Column(name = "length")
    private Integer length;

    @Column(name = "precision_value")
    private Integer precision;

    @Column(name = "scale")
    private Integer scale;

    @Column(name = "nullable")
    @Builder.Default
    private Boolean nullable = true;

    @Column(name = "default_value", length = 500)
    private String defaultValue;

    @Column(name = "is_primary_key")
    @Builder.Default
    private Boolean isPrimaryKey = false;

    @Column(name = "is_unique")
    @Builder.Default
    private Boolean isUnique = false;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;
}
