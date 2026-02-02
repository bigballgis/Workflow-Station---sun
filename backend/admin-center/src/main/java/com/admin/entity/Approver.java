package com.admin.entity;

import com.admin.enums.ApproverTargetType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 审批人配置实体
 */
@Entity
@Table(name = "sys_approvers", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"target_type", "target_id", "user_id"}))
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Approver {
    
    @Id
    @Column(length = 64)
    private String id;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private ApproverTargetType targetType;
    
    @Column(name = "target_id", nullable = false, length = 64)
    private String targetId;
    
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;
    
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
    
    @CreatedBy
    @Column(name = "created_by", length = 64, updatable = false)
    private String createdBy;
}
