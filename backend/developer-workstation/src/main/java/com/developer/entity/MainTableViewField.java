package com.developer.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "dw_main_table_view_fields")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class MainTableViewField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "view_config_id", nullable = false)
    private MainTableViewConfig viewConfig;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    @Column(name = "display_label", length = 200)
    private String displayLabel;

    @Column(name = "column_width")
    private Integer columnWidth;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "visible", nullable = false)
    @Builder.Default
    private Boolean visible = true;

    @Column(name = "is_system_field", nullable = false)
    @Builder.Default
    private Boolean isSystemField = false;
}
