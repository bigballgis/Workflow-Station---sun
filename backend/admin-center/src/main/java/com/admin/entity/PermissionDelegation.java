package com.admin.entity;

import com.admin.enums.DelegationType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 权限委托实体
 */
@Entity
@Table(name = "admin_permission_delegations")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class PermissionDelegation {
    
    @Id
    @Column(length = 64)
    private String id;
    
    @Column(name = "delegator_id", nullable = false, length = 64)
    private String delegatorId;
    
    @Column(name = "delegatee_id", nullable = false, length = 64)
    private String delegateeId;
    
    @Column(name = "permission_id", nullable = false, length = 64)
    private String permissionId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "delegation_type", nullable = false, length = 20)
    private DelegationType delegationType;
    
    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;
    
    @Column(name = "valid_to")
    private Instant validTo;
    
    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;
    
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";
    
    @Column(name = "conditions", columnDefinition = "JSONB")
    private String conditions;
    
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
    
    @CreatedBy
    @Column(name = "created_by", length = 64, updatable = false)
    private String createdBy;
    
    @Column(name = "revoked_at")
    private Instant revokedAt;
    
    @Column(name = "revoked_by", length = 64)
    private String revokedBy;
    
    @Column(name = "revoke_reason", columnDefinition = "TEXT")
    private String revokeReason;
    
    /**
     * 检查委托是否有效
     */
    public boolean isValid() {
        if (!"ACTIVE".equals(status)) {
            return false;
        }
        
        Instant now = Instant.now();
        if (now.isBefore(validFrom)) {
            return false;
        }
        
        if (validTo != null && now.isAfter(validTo)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 检查委托是否已过期
     */
    public boolean isExpired() {
        if (validTo == null) {
            return false;
        }
        return Instant.now().isAfter(validTo);
    }
}