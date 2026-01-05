package com.portal.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 工作台布局实体
 */
@Entity
@Table(name = "up_dashboard_layout", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "component_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardLayout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "component_id", nullable = false, length = 50)
    private String componentId;

    @Column(name = "component_type", nullable = false, length = 50)
    private String componentType;

    @Column(name = "grid_x", nullable = false)
    private Integer gridX;

    @Column(name = "grid_y", nullable = false)
    private Integer gridY;

    @Column(name = "grid_w", nullable = false)
    private Integer gridW;

    @Column(name = "grid_h", nullable = false)
    private Integer gridH;

    @Column(name = "is_visible")
    @Builder.Default
    private Boolean isVisible = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "jsonb")
    private Map<String, Object> config;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
