package com.portal.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

/**
 * 公共表字段定义实体（user-portal 只读引用）
 */
@Entity
@Table(name = "dw_common_field_definitions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
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

    @Column(name = "data_type", nullable = false, length = 50)
    private String dataType;

    @Column(name = "length")
    private Integer length;

    @Column(name = "is_primary_key")
    private Boolean isPrimaryKey;

    @Column(name = "nullable")
    private Boolean nullable;

    @Column(name = "sort_order")
    private Integer sortOrder;
}
