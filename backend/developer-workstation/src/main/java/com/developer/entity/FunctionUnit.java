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
