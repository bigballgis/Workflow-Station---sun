package com.developer.entity;

import com.developer.enums.FunctionUnitStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
 * 功能单元实体
 */
@Entity
@Table(name = "dw_function_units")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class FunctionUnit {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 功能单元唯一编码（英文标识符，用于系统间交互）
     * 格式：fu-{timestamp}-{random}，如 fu-20260112-a1b2c3
     */
    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;
    
    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "icon_id")
    private Icon icon;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private FunctionUnitStatus status = FunctionUnitStatus.DRAFT;
    
    @Column(name = "current_version", length = 20)
    private String currentVersion;
    
    /**
     * Semantic version number (MAJOR.MINOR.PATCH)
     */
    @Column(name = "version", nullable = false, length = 20)
    @Builder.Default
    private String version = "1.0.0";
    
    /**
     * Whether this version is currently active (only one version per function unit should be active)
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
    
    /**
     * Whether this version is enabled and visible to users
     * Only enabled versions should be displayed in the function unit list
     */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;
    
    /**
     * Timestamp when this version was deployed
     */
    @Column(name = "deployed_at", nullable = false)
    @Builder.Default
    private Instant deployedAt = Instant.now();
    
    /**
     * Reference to the previous version of this function unit
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_version_id")
    private FunctionUnit previousVersion;
    
    @CreatedBy
    @Column(name = "created_by", nullable = false, length = 50, updatable = false)
    private String createdBy;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @LastModifiedBy
    @Column(name = "updated_by", length = 50)
    private String updatedBy;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    /**
     * Version field for optimistic locking.
     * JPA will automatically increment this on each update and check for concurrent modifications.
     */
    @jakarta.persistence.Version
    @Column(name = "lock_version")
    private Long lockVersion;
    
    @JsonIgnore
    @OneToOne(mappedBy = "functionUnit", cascade = CascadeType.ALL, orphanRemoval = true)
    private ProcessDefinition processDefinition;
    
    @JsonIgnore
    @OneToMany(mappedBy = "functionUnit", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TableDefinition> tableDefinitions = new ArrayList<>();
    
    @JsonIgnore
    @OneToMany(mappedBy = "functionUnit", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<FormDefinition> formDefinitions = new ArrayList<>();
    
    @JsonIgnore
    @OneToMany(mappedBy = "functionUnit", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ActionDefinition> actionDefinitions = new ArrayList<>();
    
    @JsonIgnore
    @OneToMany(mappedBy = "functionUnit", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Version> versions = new ArrayList<>();
}
