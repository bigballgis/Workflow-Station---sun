package com.developer.entity;

import com.developer.enums.MainTableViewStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "dw_main_table_view_configs")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class MainTableViewConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "function_unit_id", nullable = false)
    private FunctionUnit functionUnit;

    @Column(name = "main_table_id", nullable = false)
    private Long mainTableId;

    @Column(name = "view_name", nullable = false, length = 200)
    private String viewName;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sort_config", columnDefinition = "jsonb")
    private List<Map<String, Object>> sortConfig;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filter_config", columnDefinition = "jsonb")
    private Map<String, Object> filterConfig;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private MainTableViewStatus status = MainTableViewStatus.DRAFT;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "viewConfig", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<MainTableViewField> viewFields = new ArrayList<>();

    @Column(name = "restrict_to_involved_users", nullable = false)
    @Builder.Default
    private Boolean restrictToInvolvedUsers = false;

    @OneToMany(mappedBy = "viewConfig", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MainTableViewAccess> accessRules = new ArrayList<>();
}
