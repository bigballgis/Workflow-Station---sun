package com.admin.entity;

import com.admin.enums.PermissionRequestStatus;
import com.admin.enums.PermissionRequestType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 权限申请实体
 */
@Entity
@Table(name = "sys_permission_requests")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class PermissionRequest {
    
    @Id
    @Column(length = 64)
    private String id;
    
    @Column(name = "applicant_id", nullable = false, length = 64)
    private String applicantId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 20)
    private PermissionRequestType requestType;
    
    @Column(name = "target_id", nullable = false, length = 64)
    private String targetId;
    
    /**
     * @deprecated 角色现在通过虚拟组获取，不再在申请中指定
     * 保留此字段用于向后兼容，新申请不应使用此字段
     */
    @Deprecated
    @Column(name = "role_ids", columnDefinition = "TEXT")
    private String roleIds;  // JSON array of role IDs (deprecated)
    
    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PermissionRequestStatus status = PermissionRequestStatus.PENDING;
    
    @Column(name = "approver_id", length = 64)
    private String approverId;
    
    @Column(name = "approver_comment", columnDefinition = "TEXT")
    private String approverComment;
    
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @Column(name = "approved_at")
    private Instant approvedAt;
}
