package com.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 用户偏好设置实体
 * 用于存储用户的各种偏好设置，如"不再提醒"等
 */
@Entity
@Table(name = "sys_user_preferences", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "preference_key"})
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class UserPreference {
    
    @Id
    @Column(length = 64)
    private String id;
    
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;
    
    @Column(name = "preference_key", nullable = false, length = 100)
    private String preferenceKey;
    
    @Column(name = "preference_value", length = 500)
    private String preferenceValue;
    
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    /**
     * 偏好设置键常量
     */
    public static final String KEY_DONT_REMIND_BU_APPLICATION = "dont_remind_bu_application";
}
