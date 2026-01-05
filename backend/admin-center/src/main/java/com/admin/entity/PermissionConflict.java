package com.admin.entity;

import com.admin.enums.ConflictResolutionStrategy;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 权限冲突实体
 */
@Entity
@Table(name = "admin_permission_conflicts")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class PermissionConflict {
    
    @Id
    @Column(length = 64)
    private String id;
    
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;
    
    @Column(name = "conflict_source1", nullable = false, length = 100)
    private String conflictSource1;
    
    @Column(name = "conflict_source2", nullable = false, length = 100)
    private String conflictSource2;
    
    @Column(name = "conflict_description", columnDefinition = "TEXT")
    private String conflictDescription;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_strategy", length = 30)
    private ConflictResolutionStrategy resolutionStrategy;
    
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";
    
    @Column(name = "resolution_result", columnDefinition = "TEXT")
    private String resolutionResult;
    
    @CreatedDate
    @Column(name = "detected_at", updatable = false)
    private Instant detectedAt;
    
    @Column(name = "resolved_at")
    private Instant resolvedAt;
    
    @Column(name = "resolved_by", length = 64)
    private String resolvedBy;
    
    /**
     * 检查冲突是否已解决
     */
    public boolean isResolved() {
        return "RESOLVED".equals(status);
    }
    
    /**
     * 检查冲突是否需要手动处理
     */
    public boolean requiresManualResolution() {
        return resolutionStrategy == ConflictResolutionStrategy.MANUAL;
    }
}