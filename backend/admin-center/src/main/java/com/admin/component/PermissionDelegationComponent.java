package com.admin.component;

import com.admin.dto.request.PermissionDelegationRequest;
import com.admin.dto.response.PermissionDelegationResult;
import com.platform.security.entity.Permission;
import com.admin.entity.PermissionDelegation;
import com.admin.enums.DelegationType;
import com.admin.exception.AdminBusinessException;
import com.admin.exception.PermissionNotFoundException;
import com.admin.repository.PermissionDelegationRepository;
import com.admin.repository.PermissionRepository;
import com.platform.common.audit.Audited;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 权限委托管理组件
 * 负责权限委托的创建、管理和回收
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionDelegationComponent {
    
    private final PermissionDelegationRepository delegationRepository;
    private final PermissionRepository permissionRepository;
    
    /**
     * 创建权限委托
     */
    @Transactional
    @Audited(action = "PERMISSION_DELEGATE", resourceType = "PERMISSION", resourceId = "#request.permissionId")
    public PermissionDelegationResult createDelegation(PermissionDelegationRequest request) {
        log.info("Creating permission delegation from {} to {} for permission {}", 
                request.getDelegatorId(), request.getDelegateeId(), request.getPermissionId());
        
        // 验证权限存在
        Permission permission = permissionRepository.findById(request.getPermissionId())
                .orElseThrow(() -> new PermissionNotFoundException(request.getPermissionId()));
        
        // 验证委托人不能委托给自己
        if (request.getDelegatorId().equals(request.getDelegateeId())) {
            throw new AdminBusinessException("SELF_DELEGATION_NOT_ALLOWED", "不能将权限委托给自己");
        }
        
        // 验证时间范围
        if (request.getValidTo() != null && request.getValidFrom().isAfter(request.getValidTo())) {
            throw new AdminBusinessException("INVALID_TIME_RANGE", "生效时间不能晚于失效时间");
        }
        
        // 检查是否存在冲突的委托
        List<PermissionDelegation> existingDelegations = delegationRepository
                .findActiveDelegationsByDelegatorAndPermission(
                        request.getDelegatorId(), 
                        request.getPermissionId(), 
                        Instant.now());
        
        if (!existingDelegations.isEmpty() && request.getDelegationType() == DelegationType.TRANSFER) {
            throw new AdminBusinessException("DELEGATION_CONFLICT", 
                    "该权限已存在转移委托，不能重复委托");
        }
        
        // 创建委托记录
        PermissionDelegation delegation = PermissionDelegation.builder()
                .id(UUID.randomUUID().toString())
                .delegatorId(request.getDelegatorId())
                .delegateeId(request.getDelegateeId())
                .permissionId(request.getPermissionId())
                .delegationType(request.getDelegationType())
                .validFrom(request.getValidFrom())
                .validTo(request.getValidTo())
                .reason(request.getReason())
                .conditions(request.getConditions())
                .status("ACTIVE")
                .build();
        
        delegationRepository.save(delegation);
        
        log.info("Permission delegation created successfully: {}", delegation.getId());
        return PermissionDelegationResult.success(delegation, permission);
    }
    
    /**
     * 撤销权限委托
     */
    @Transactional
    @Audited(action = "PERMISSION_DELEGATE_REVOKE", resourceType = "PERMISSION", resourceId = "#delegationId")
    public void revokeDelegation(String delegationId, String revokedBy, String reason) {
        log.info("Revoking permission delegation: {}", delegationId);
        
        PermissionDelegation delegation = delegationRepository.findById(delegationId)
                .orElseThrow(() -> new AdminBusinessException("DELEGATION_NOT_FOUND", 
                        "权限委托不存在: " + delegationId));
        
        if (!"ACTIVE".equals(delegation.getStatus())) {
            throw new AdminBusinessException("DELEGATION_NOT_ACTIVE", "权限委托已失效");
        }
        
        delegation.setStatus("REVOKED");
        delegation.setRevokedAt(Instant.now());
        delegation.setRevokedBy(revokedBy);
        delegation.setRevokeReason(reason);
        
        delegationRepository.save(delegation);
        
        log.info("Permission delegation revoked successfully: {}", delegationId);
    }
    
    /**
     * 获取用户的委托权限
     */
    public List<PermissionDelegationResult> getUserDelegatedPermissions(String userId) {
        List<PermissionDelegation> delegations = delegationRepository
                .findActiveUserDelegations(userId, Instant.now());
        
        return delegations.stream()
                .map(delegation -> {
                    Permission permission = permissionRepository.findById(delegation.getPermissionId()).orElse(null);
                    return PermissionDelegationResult.fromEntity(delegation, permission);
                })
                .collect(Collectors.toList());
    }
    
    /**
     * 获取用户委托出去的权限
     */
    public List<PermissionDelegationResult> getUserDelegatedOutPermissions(String userId) {
        List<PermissionDelegation> delegations = delegationRepository.findByDelegatorId(userId);
        
        return delegations.stream()
                .filter(d -> "ACTIVE".equals(d.getStatus()))
                .map(delegation -> {
                    Permission permission = permissionRepository.findById(delegation.getPermissionId()).orElse(null);
                    return PermissionDelegationResult.fromEntity(delegation, permission);
                })
                .collect(Collectors.toList());
    }
    
    /**
     * 检查用户是否拥有委托权限
     */
    public boolean hasDelegatedPermission(String userId, String permissionId) {
        List<PermissionDelegation> delegations = delegationRepository
                .findActiveDelegationsByDelegateeAndPermission(userId, permissionId, Instant.now());
        
        return !delegations.isEmpty();
    }
    
    /**
     * 自动回收过期的权限委托
     */
    @Transactional
    public void revokeExpiredDelegations() {
        log.info("Starting automatic revocation of expired delegations");
        
        List<PermissionDelegation> expiredDelegations = delegationRepository
                .findExpiredDelegations(Instant.now());
        
        for (PermissionDelegation delegation : expiredDelegations) {
            delegation.setStatus("EXPIRED");
            delegation.setRevokedAt(Instant.now());
            delegation.setRevokedBy("SYSTEM");
            delegation.setRevokeReason("自动过期回收");
            
            delegationRepository.save(delegation);
        }
        
        log.info("Revoked {} expired delegations", expiredDelegations.size());
    }
    
    /**
     * 批量撤销用户的所有委托权限（用于用户离职等场景）
     */
    @Transactional
    public void revokeAllUserDelegations(String userId, String revokedBy, String reason) {
        log.info("Revoking all delegations for user: {}", userId);
        
        // 撤销用户委托出去的权限
        List<PermissionDelegation> delegatedOut = delegationRepository.findByDelegatorId(userId);
        for (PermissionDelegation delegation : delegatedOut) {
            if ("ACTIVE".equals(delegation.getStatus())) {
                revokeDelegation(delegation.getId(), revokedBy, reason);
            }
        }
        
        // 撤销委托给用户的权限
        List<PermissionDelegation> delegatedIn = delegationRepository.findByDelegateeId(userId);
        for (PermissionDelegation delegation : delegatedIn) {
            if ("ACTIVE".equals(delegation.getStatus())) {
                revokeDelegation(delegation.getId(), revokedBy, reason);
            }
        }
        
        log.info("Revoked all delegations for user: {}", userId);
    }
}