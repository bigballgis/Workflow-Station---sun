package com.admin.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * 密码历史实体
 */
@Entity
@Table(name = "admin_password_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class PasswordHistory {
    
    @Id
    @Column(length = 64)
    private String id;
    
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;
    
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    
    @Column(name = "created_at")
    private Instant createdAt;
}
