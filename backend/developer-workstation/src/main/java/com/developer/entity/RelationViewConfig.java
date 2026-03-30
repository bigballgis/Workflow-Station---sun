package com.developer.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Relation Table View 配置实体
 * 存储绑定 Relation Table 后自动生成的 View 页面配置
 */
@Entity
@Table(name = "rt_view_configs")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class RelationViewConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "binding_id", nullable = false)
    private Long bindingId;

    @Column(name = "table_id", nullable = false)
    private Long tableId;

    @Column(name = "field_config", columnDefinition = "TEXT")
    private String fieldConfig;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "viewConfig", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<RelationViewField> viewFields = new ArrayList<>();
}
