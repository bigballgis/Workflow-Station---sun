package com.portal.entity;

import com.portal.enums.PermissionRequestStatus;
import com.portal.enums.PermissionRequestType;
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
import java.util.List;

/**
 * 权限申请实体
 */
@Entity
@Table(name = "up_permission_request")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PermissionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "applicant_id", nullable = false, length = 64)
    private String applicantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 30)
    private PermissionRequestType requestType;

    // ========== 新字段 - 角色申请 ==========
    
    /** 申请的角色ID */
    @Column(name = "role_id", length = 64)
    private String roleId;
    
    /** 申请的角色名称（冗余存储，方便显示） */
    @Column(name = "role_name", length = 100)
    private String roleName;
    
    /** 目标组织单元ID */
    @Column(name = "organization_unit_id", length = 64)
    private String organizationUnitId;
    
    /** 目标组织单元名称（冗余存储，方便显示） */
    @Column(name = "organization_unit_name", length = 200)
    private String organizationUnitName;
    
    // ========== 新字段 - 虚拟组申请 ==========
    
    /** 申请加入的虚拟组ID */
    @Column(name = "virtual_group_id", length = 64)
    private String virtualGroupId;
    
    /** 申请加入的虚拟组名称（冗余存储，方便显示） */
    @Column(name = "virtual_group_name", length = 200)
    private String virtualGroupName;

    // ========== 旧字段（已废弃，保留兼容） ==========
    
    /** @deprecated 使用 roleId/virtualGroupId 替代 */
    @Deprecated
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "permissions", columnDefinition = "jsonb")
    private List<String> permissions;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    /** @deprecated 新的申请类型不需要有效期 */
    @Deprecated
    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    /** @deprecated 新的申请类型不需要有效期 */
    @Deprecated
    @Column(name = "valid_to")
    private LocalDateTime validTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    @Builder.Default
    private PermissionRequestStatus status = PermissionRequestStatus.PENDING;

    @Column(name = "approver_id", length = 64)
    private String approverId;

    @Column(name = "approve_time")
    private LocalDateTime approveTime;

    @Column(name = "approve_comment", columnDefinition = "TEXT")
    private String approveComment;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
