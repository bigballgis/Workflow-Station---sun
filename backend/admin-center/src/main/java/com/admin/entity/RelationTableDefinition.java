package com.admin.entity;

import com.platform.common.enums.RelationTableStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Relation Table 定义实体
 * 包含表名、显示名、描述、状态、版本等元数据
 */
@Entity
@Table(name = "rt_table_definitions")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class RelationTableDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "table_name", nullable = false, unique = true, length = 100)
    private String tableName;

    @Column(name = "display_name", length = 200)
    private String displayName;

    /**
     * Display name at last successful deploy; Table Data reads this when status is UPDATED/ROLLBACK.
     */
    @Column(name = "deployed_display_name", length = 200)
    private String deployedDisplayName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private RelationTableStatus status = RelationTableStatus.INIT;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "portal_visible", nullable = false)
    @Builder.Default
    private Boolean portalVisible = false;

    @Column(name = "current_version")
    @Builder.Default
    private Integer currentVersion = 0;

    /**
     * Optional Function Unit grouping (sys_function_units.id); null = ungrouped.
     */
    @Column(name = "function_unit_id", length = 64)
    private String functionUnitId;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", length = 64, updatable = false)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @OneToMany(mappedBy = "tableDefinition", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<RelationFieldDefinition> fieldDefinitions = new ArrayList<>();

    @OneToMany(mappedBy = "tableDefinition", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("versionNumber DESC")
    @Builder.Default
    private List<RelationTableVersion> versions = new ArrayList<>();
}
