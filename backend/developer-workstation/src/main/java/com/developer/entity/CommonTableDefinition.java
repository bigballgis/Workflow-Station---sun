package com.developer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 公共表定义实体
 * 与 function unit 无关的独立共享表结构
 */
@Entity
@Table(name = "dw_common_table_definitions")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class CommonTableDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 100)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "DRAFT";

    @Column(name = "version", length = 20)
    @Builder.Default
    private String version = "1.0.0";

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = false;

    @Column(name = "deployed_at")
    private Instant deployedAt;

    @Column(name = "deployed_by", length = 100)
    private String deployedBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @OneToMany(mappedBy = "commonTable", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<CommonFieldDefinition> fieldDefinitions = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
