package com.admin.service;

import com.admin.entity.Approver;
import com.platform.security.entity.User;
import com.admin.enums.ApproverTargetType;
import com.admin.exception.AdminBusinessException;
import com.admin.exception.BusinessUnitNotFoundException;
import com.admin.exception.UserNotFoundException;
import com.admin.exception.VirtualGroupNotFoundException;
import com.admin.repository.ApproverRepository;
import com.admin.repository.BusinessUnitRepository;
import com.admin.repository.UserRepository;
import com.admin.repository.VirtualGroupRepository;
import com.platform.common.i18n.I18nService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 审批人配置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApproverService {
    
    private final ApproverRepository approverRepository;
    private final UserRepository userRepository;
    private final VirtualGroupRepository virtualGroupRepository;
    private final BusinessUnitRepository businessUnitRepository;
    private final I18nService i18nService;
    
    /**
     * 添加审批人
     */
    @Transactional
    public void addApprover(ApproverTargetType targetType, String targetId, String userId) {
        log.info("Adding approver {} for {} {}", userId, targetType, targetId);
        
        // 验证目标存在
        validateTargetExists(targetType, targetId);
        
        // 验证用户存在且活跃
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        
        if (!user.isActive()) {
            throw new AdminBusinessException("USER_NOT_ACTIVE", i18nService.getMessage("admin.user_not_active"));
        }
        
        // 检查是否已是审批人
        if (approverRepository.existsByTargetTypeAndTargetIdAndUserId(targetType, targetId, userId)) {
            throw new AdminBusinessException("ALREADY_APPROVER", i18nService.getMessage("admin.already_approver"));
        }
        
        Approver approver = Approver.builder()
                .id(UUID.randomUUID().toString())
                .targetType(targetType)
                .targetId(targetId)
                .userId(userId)
                .build();
        
        approverRepository.save(approver);
        log.info("Approver {} added for {} {} successfully", userId, targetType, targetId);
    }
    
    /**
     * 移除审批人
     */
    @Transactional
    public void removeApprover(String approverId) {
        log.info("Removing approver {}", approverId);
        
        Approver approver = approverRepository.findById(approverId)
                .orElseThrow(() -> new AdminBusinessException("APPROVER_NOT_FOUND", i18nService.getMessage("admin.approver_not_found")));
        
        approverRepository.delete(approver);
        log.info("Approver {} removed successfully", approverId);
    }
    
    /**
     * 移除指定目标的审批人
     */
    @Transactional
    public void removeApproverByTarget(ApproverTargetType targetType, String targetId, String userId) {
        log.info("Removing approver {} from {} {}", userId, targetType, targetId);
        
        Approver approver = approverRepository.findByTargetTypeAndTargetIdAndUserId(targetType, targetId, userId)
                .orElseThrow(() -> new AdminBusinessException("APPROVER_NOT_FOUND", i18nService.getMessage("admin.not_approver")));
        
        approverRepository.delete(approver);
        log.info("Approver {} removed from {} {} successfully", userId, targetType, targetId);
    }
    
    /**
     * 获取目标的审批人列表
     */
    public List<User> getApprovers(ApproverTargetType targetType, String targetId) {
        List<Approver> approvers = approverRepository.findByTargetTypeAndTargetId(targetType, targetId);
        
        // Extract user IDs
        List<String> userIds = approvers.stream()
                .map(Approver::getUserId)
                .distinct()
                .collect(Collectors.toList());
        
        // Batch fetch users
        return userRepository.findAllById(userIds);
    }
    
    /**
     * 获取目标的审批人配置列表
     */
    public List<Approver> getApproverConfigs(ApproverTargetType targetType, String targetId) {
        return approverRepository.findByTargetTypeAndTargetIdWithUser(targetType, targetId);
    }
    
    /**
     * 检查用户是否是指定目标的审批人
     */
    public boolean isApprover(String userId, ApproverTargetType targetType, String targetId) {
        return approverRepository.existsByTargetTypeAndTargetIdAndUserId(targetType, targetId, userId);
    }
    
    /**
     * 检查用户是否是任何目标的审批人
     */
    public boolean isAnyApprover(String userId) {
        return approverRepository.existsByUserId(userId);
    }
    
    /**
     * 检查目标是否有审批人
     */
    public boolean hasApprover(ApproverTargetType targetType, String targetId) {
        return approverRepository.existsByTargetTypeAndTargetId(targetType, targetId);
    }
    
    /**
     * 获取用户作为审批人的所有虚拟组ID
     */
    public List<String> getApproverVirtualGroupIds(String userId) {
        return approverRepository.findVirtualGroupIdsByApproverUserId(userId);
    }
    
    /**
     * 获取用户作为审批人的所有业务单元ID
     */
    public List<String> getApproverBusinessUnitIds(String userId) {
        return approverRepository.findBusinessUnitIdsByApproverUserId(userId);
    }
    
    /**
     * 获取所有有审批人的业务单元ID
     */
    public List<String> getBusinessUnitIdsWithApprover() {
        return approverRepository.findDistinctTargetIdsByTargetType(ApproverTargetType.BUSINESS_UNIT);
    }
    
    /**
     * 验证目标存在
     */
    private void validateTargetExists(ApproverTargetType targetType, String targetId) {
        switch (targetType) {
            case VIRTUAL_GROUP:
                if (!virtualGroupRepository.existsById(targetId)) {
                    throw new VirtualGroupNotFoundException(targetId);
                }
                break;
            case BUSINESS_UNIT:
                if (!businessUnitRepository.existsById(targetId)) {
                    throw new BusinessUnitNotFoundException(targetId);
                }
                break;
            default:
                throw new AdminBusinessException("INVALID_TARGET_TYPE", i18nService.getMessage("admin.invalid_target_type", targetType));
        }
    }
}
