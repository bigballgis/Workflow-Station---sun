package com.developer.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

/**
 * Relation Table View 字段实体
 * 存储 View 页面中展示的字段配置
 */
@Entity
@Table(name = "rt_view_fields")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class RelationViewField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "view_config_id", nullable = false)
    private RelationViewConfig viewConfig;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    @Column(name = "display_label", length = 200)
    private String displayLabel;

    @Column(name = "column_width")
    private Integer columnWidth;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "visible", nullable = false)
    @Builder.Default
    private Boolean visible = true;
}
