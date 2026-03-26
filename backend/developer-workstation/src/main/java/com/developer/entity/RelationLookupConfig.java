package com.developer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Relation Table Lookup 配置实体
 * 存储 form-create Lookup 组件的配置信息
 */
@Entity
@Table(name = "rt_lookup_configs")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class RelationLookupConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "form_id", nullable = false)
    private Long formId;

    @Column(name = "component_id", nullable = false, length = 100)
    private String componentId;

    @Column(name = "view_config_id")
    private Long viewConfigId;

    @Column(name = "table_id", nullable = false)
    private Long tableId;

    @Column(name = "search_fields", columnDefinition = "TEXT")
    private String searchFields;

    @Column(name = "display_field", length = 100)
    private String displayField;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
