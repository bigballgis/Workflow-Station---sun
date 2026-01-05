package com.portal.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 用户偏好设置实体
 */
@Entity
@Table(name = "up_user_preference")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true, length = 64)
    private String userId;

    @Column(name = "theme", length = 20)
    @Builder.Default
    private String theme = "light";

    @Column(name = "theme_color", length = 20)
    @Builder.Default
    private String themeColor = "#DB0011";

    @Column(name = "font_size", length = 10)
    @Builder.Default
    private String fontSize = "medium";

    @Column(name = "layout_density", length = 10)
    @Builder.Default
    private String layoutDensity = "normal";

    @Column(name = "language", length = 10)
    @Builder.Default
    private String language = "zh-CN";

    @Column(name = "timezone", length = 50)
    @Builder.Default
    private String timezone = "Asia/Shanghai";

    @Column(name = "date_format", length = 20)
    @Builder.Default
    private String dateFormat = "YYYY-MM-DD";

    @Column(name = "page_size")
    @Builder.Default
    private Integer pageSize = 20;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
