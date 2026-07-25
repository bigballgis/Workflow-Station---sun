package com.developer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 用户级 UI 偏好（跨设备/跨浏览器跟随账号）。
 * 当前用途：FU 列表 Launchpad 布局（排序 + 分组）。value 为前端自定义 JSON 字符串，
 * 后端不解析内容，仅按 (userId, prefKey) 唯一存取。
 */
@Entity
@Table(name = "dw_user_preferences",
        uniqueConstraints = @UniqueConstraint(name = "uk_dw_user_pref", columnNames = {"user_id", "pref_key"}))
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class UserPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "pref_key", nullable = false, length = 64)
    private String prefKey;

    @Column(name = "pref_value", nullable = false, columnDefinition = "TEXT")
    private String prefValue;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
