package com.admin.entity;

import com.admin.enums.ApproverTargetType;
import com.admin.enums.MemberChangeType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 成员变更记录实体（审计日志）
 */
@Entity
@Table(name = "sys_member_change_logs")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class MemberChangeLog {
    
    @Id
    @Column(length = 64)
    private String id;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 20)
    private MemberChangeType changeType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private ApproverTargetType targetType;
    
    @Column(name = "target_id", nullable = false, length = 64)
    private String targetId;
    
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;
    
    @Column(name = "role_ids", columnDefinition = "TEXT")
    private String roleIds;  // JSON array of role IDs
    
    @Column(name = "operator_id", length = 64)
    private String operatorId;
    
    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;
    
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id", insertable = false, updatable = false)
    private User operator;
}
