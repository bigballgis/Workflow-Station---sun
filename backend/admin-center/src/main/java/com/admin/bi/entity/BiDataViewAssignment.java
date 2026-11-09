package com.admin.bi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Dashboard binding for one User Portal Table View, named by released data: the function unit code and the View
 * name (unique within a function unit). The View is resolved in the unit's active release on every read, so a
 * binding survives redeployment and shows as unavailable while the active release has no such View.
 */
@Entity
@Table(name = "bi_data_view_assignment",
        uniqueConstraints = @UniqueConstraint(columnNames = {"dashboard_id", "function_unit_code", "view_name"}),
        indexes = {
                @Index(name = "idx_bi_data_view_assignment_release_view",
                        columnList = "function_unit_code, view_name, enabled"),
                @Index(name = "idx_bi_data_view_assignment_dashboard", columnList = "dashboard_id")
        })
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class BiDataViewAssignment {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "dashboard_id", nullable = false, length = 64)
    private String dashboardId;

    @Column(name = "function_unit_code", length = 100)
    private String functionUnitCode;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /** Null only for a legacy binding that could not be carried over (schema 103). */
    @Column(name = "view_name", length = 255)
    private String viewName;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @CreatedBy
    @Column(name = "created_by", length = 64, updatable = false)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by", length = 64)
    private String updatedBy;
}
