package com.admin.service;

import com.admin.entity.PermissionRequest;
import com.platform.security.entity.Role;
import com.admin.enums.ApproverTargetType;
import com.admin.enums.PermissionRequestStatus;
import com.admin.enums.PermissionRequestType;
import com.admin.enums.RoleType;
import com.admin.exception.AdminBusinessException;
import com.admin.exception.BusinessUnitNotFoundException;
import com.admin.exception.VirtualGroupNotFoundException;
import com.admin.repository.BusinessUnitRepository;
import com.admin.repository.PermissionRequestRepository;
import com.admin.repository.VirtualGroupMemberRepository;
import com.admin.repository.VirtualGroupRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 权限申请服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionRequestService {
    
    private final PermissionRequestRepository permissionRequestRepository;
    private final VirtualGroupRepository virtualGroupRepository;
    private final BusinessUnitRepository businessUnitRepository;
    private final VirtualGroupMemberRepository virtualGroupMemberRepository;
    private final ApproverService approverService;
    private final MemberManagementService memberManagementService;
    private final VirtualGroupRoleService virtualGroupRoleService;
    private final ObjectMapper objectMapper;
    
    /**
     * 创建虚拟组申请
     */
    @Transactional
    public PermissionRequest createVirtualGroupRequest(String applicantId, String virtualGroupId, String reason) {
        log.info("Creating virtual group request: applicant={}, virtualGroup={}", applicantId, virtualGroupId);
        
        // 验证虚拟组存在
        if (!virtualGroupRepository.existsById(virtualGroupId)) {
            throw new VirtualGroupNotFoundException(virtualGroupId);
        }
        
        // 验证虚拟组有审批人
        if (!approverService.hasApprover(ApproverTargetType.VIRTUAL_GROUP, virtualGroupId)) {
            throw new AdminBusinessException("NO_APPROVER", "该虚拟组未配置审批人，无法申请");
        }
        
        // 检查是否存在待审批的申请
        if (permissionRequestRepository.existsByApplicantIdAndTargetIdAndRequestTypeAndStatus(
                applicantId, virtualGroupId, PermissionRequestType.VIRTUAL_GROUP, PermissionRequestStatus.PENDING)) {
            throw new AdminBusinessException("DUPLICATE_REQUEST", "已存在待审批的虚拟组申请");
        }
        
        PermissionRequest request = PermissionRequest.builder()
                .id(UUID.randomUUID().toString())
                .applicantId(applicantId)
                .requestType(PermissionRequestType.VIRTUAL_GROUP)
                .targetId(virtualGroupId)
                .reason(reason)
                .status(PermissionRequestStatus.PENDING)
                .build();
        
        permissionRequestRepository.save(request);
        log.info("Virtual group request created: {}", request.getId());
        return request;
    }
    
    /**
     * 创建业务单元申请（新版本：不需要选择角色，角色通过虚拟组获取）
     * 用户必须拥有至少一个 BU_BOUNDED 角色才能申请加入业务单元
     */
    @Transactional
    public PermissionRequest createBusinessUnitRequest(String applicantId, String businessUnitId, String reason) {
        log.info("Creating business unit request: applicant={}, businessUnit={}", applicantId, businessUnitId);
        
        // 验证业务单元存在
        if (!businessUnitRepository.existsById(businessUnitId)) {
            throw new BusinessUnitNotFoundException(businessUnitId);
        }
        
        // 验证业务单元有审批人
        if (!approverService.hasApprover(ApproverTargetType.BUSINESS_UNIT, businessUnitId)) {
            throw new AdminBusinessException("NO_APPROVER", "该业务单元未配置审批人，无法申请");
        }
        
        // 验证用户是否有 BU_BOUNDED 角色（通过虚拟组获取）
        List<Role> userBuBoundedRoles = getUserBuBoundedRoles(applicantId);
        if (userBuBoundedRoles.isEmpty()) {
            throw new AdminBusinessException("NO_BU_BOUNDED_ROLE", 
                    "您没有 BU-Bounded 类型的角色，请先申请加入包含 BU-Bounded 角色的虚拟组");
        }
        
        // 检查是否存在待审批的申请
        if (permissionRequestRepository.existsByApplicantIdAndTargetIdAndRequestTypeAndStatus(
                applicantId, businessUnitId, PermissionRequestType.BUSINESS_UNIT, PermissionRequestStatus.PENDING)) {
            throw new AdminBusinessException("DUPLICATE_REQUEST", "已存在待审批的业务单元申请");
        }
        
        PermissionRequest request = PermissionRequest.builder()
                .id(UUID.randomUUID().toString())
                .applicantId(applicantId)
                .requestType(PermissionRequestType.BUSINESS_UNIT)
                .targetId(businessUnitId)
                .reason(reason)
                .status(PermissionRequestStatus.PENDING)
                .build();
        
        permissionRequestRepository.save(request);
        log.info("Business unit request created: {}", request.getId());
        return request;
    }
    
    /**
     * 创建业务单元角色申请（已废弃，保留向后兼容）
     * @deprecated 使用 {@link #createBusinessUnitRequest(String, String, String)} 代替
     */
    @Deprecated
    @Transactional
    public PermissionRequest createBusinessUnitRoleRequest(String applicantId, String businessUnitId, 
                                                            List<String> roleIds, String reason) {
        log.warn("Using deprecated createBusinessUnitRoleRequest, redirecting to createBusinessUnitRequest");
        return createBusinessUnitRequest(applicantId, businessUnitId, reason);
    }
    
    /**
     * 获取用户的 BU_BOUNDED 角色列表（通过虚拟组获取）
     */
    public List<Role> getUserBuBoundedRoles(String userId) {
        // 获取用户所属的所有虚拟组
        List<String> virtualGroupIds = virtualGroupMemberRepository.findVirtualGroupIdsByUserId(userId);
        
        // 获取每个虚拟组绑定的角色，筛选出 BU_BOUNDED 类型
        return virtualGroupIds.stream()
                .map(virtualGroupRoleService::getBoundRole)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(role -> "BU_BOUNDED".equals(role.getType()))
                .distinct()
                .toList();
    }
    
    /**
     * 获取用户可申请的业务单元列表
     * 只返回用户尚未加入且有审批人的业务单元
     */
    public List<com.platform.security.entity.BusinessUnit> getApplicableBusinessUnits(String userId) {
        // 获取用户已加入的业务单元ID
        List<String> joinedBusinessUnitIds = memberManagementService.getUserBusinessUnitIds(userId);
        
        // 获取所有有审批人的业务单元
        List<String> businessUnitIdsWithApprover = approverService.getBusinessUnitIdsWithApprover();
        
        // 返回用户未加入且有审批人的业务单元
        return businessUnitRepository.findAllById(businessUnitIdsWithApprover).stream()
                .filter(bu -> !joinedBusinessUnitIds.contains(bu.getId()))
                .toList();
    }
    
    /**
     * 批准申请
     */
    @Transactional
    public void approve(String requestId, String approverId, String comment) {
        log.info("Approving request: requestId={}, approverId={}", requestId, approverId);
        
        PermissionRequest request = getAndValidateRequest(requestId, approverId);
        
        // 更新申请状态
        request.setStatus(PermissionRequestStatus.APPROVED);
        request.setApproverId(approverId);
        request.setApproverComment(comment);
        request.setApprovedAt(Instant.now());
        permissionRequestRepository.save(request);
        
        // 执行审批通过后的操作
        memberManagementService.processApprovedRequest(request);
        
        log.info("Request {} approved by {}", requestId, approverId);
    }
    
    /**
     * 拒绝申请
     */
    @Transactional
    public void reject(String requestId, String approverId, String comment) {
        log.info("Rejecting request: requestId={}, approverId={}", requestId, approverId);
        
        // 验证拒绝时必须提供意见
        if (comment == null || comment.trim().isEmpty()) {
            throw new AdminBusinessException("COMMENT_REQUIRED", "拒绝时必须提供审批意见");
        }
        
        PermissionRequest request = getAndValidateRequest(requestId, approverId);
        
        // 更新申请状态
        request.setStatus(PermissionRequestStatus.REJECTED);
        request.setApproverId(approverId);
        request.setApproverComment(comment);
        request.setApprovedAt(Instant.now());
        permissionRequestRepository.save(request);
        
        log.info("Request {} rejected by {}", requestId, approverId);
    }
    
    /**
     * 取消申请
     */
    @Transactional
    public void cancel(String requestId, String userId) {
        log.info("Cancelling request: requestId={}, userId={}", requestId, userId);
        
        PermissionRequest request = permissionRequestRepository.findById(requestId)
                .orElseThrow(() -> new AdminBusinessException("REQUEST_NOT_FOUND", "申请不存在"));
        
        // 验证是申请人本人
        if (!request.getApplicantId().equals(userId)) {
            throw new AdminBusinessException("NOT_APPLICANT", "只能取消自己的申请");
        }
        
        // 验证申请状态
        if (request.getStatus() != PermissionRequestStatus.PENDING) {
            throw new AdminBusinessException("INVALID_STATUS", "只能取消待审批的申请");
        }
        
        request.setStatus(PermissionRequestStatus.CANCELLED);
        permissionRequestRepository.save(request);
        
        log.info("Request {} cancelled by {}", requestId, userId);
    }
    
    /**
     * 获取审批人的待审批列表
     */
    public List<PermissionRequest> getPendingRequestsForApprover(String approverId) {
        // 获取审批人负责的虚拟组和业务单元
        List<String> virtualGroupIds = approverService.getApproverVirtualGroupIds(approverId);
        List<String> businessUnitIds = approverService.getApproverBusinessUnitIds(approverId);
        
        // 合并所有目标ID
        virtualGroupIds.addAll(businessUnitIds);
        
        if (virtualGroupIds.isEmpty()) {
            return List.of();
        }
        
        // 获取待审批申请（排除自己的申请）
        return permissionRequestRepository.findPendingByTargetIdsExcludingApplicant(virtualGroupIds, approverId);
    }
    
    /**
     * 获取申请人的申请记录
     */
    public List<PermissionRequest> getRequestsByApplicant(String applicantId) {
        return permissionRequestRepository.findByApplicantIdWithApplicant(applicantId);
    }
    
    /**
     * 分页获取所有申请记录
     */
    public Page<PermissionRequest> getAllRequests(PermissionRequestStatus status, 
                                                   PermissionRequestType requestType,
                                                   String applicantId,
                                                   Instant startDate,
                                                   Instant endDate,
                                                   Pageable pageable) {
        return permissionRequestRepository.findByConditions(status, requestType, applicantId, startDate, endDate, pageable);
    }
    
    /**
     * 获取申请详情
     */
    public PermissionRequest getRequestDetail(String requestId) {
        return permissionRequestRepository.findByIdWithDetails(requestId)
                .orElseThrow(() -> new AdminBusinessException("REQUEST_NOT_FOUND", "申请不存在"));
    }
    
    /**
     * 获取并验证申请
     */
    private PermissionRequest getAndValidateRequest(String requestId, String approverId) {
        PermissionRequest request = permissionRequestRepository.findById(requestId)
                .orElseThrow(() -> new AdminBusinessException("REQUEST_NOT_FOUND", "申请不存在"));
        
        // 验证申请状态
        if (request.getStatus() != PermissionRequestStatus.PENDING) {
            throw new AdminBusinessException("INVALID_STATUS", "该申请已处理，无法再次审批");
        }
        
        // 验证不能审批自己的申请
        if (request.getApplicantId().equals(approverId)) {
            throw new AdminBusinessException("SELF_APPROVAL", "不能审批自己的申请");
        }
        
        // 验证审批人权限
        ApproverTargetType targetType;
        if (request.getRequestType() == PermissionRequestType.VIRTUAL_GROUP) {
            targetType = ApproverTargetType.VIRTUAL_GROUP;
        } else {
            // BUSINESS_UNIT 和 BUSINESS_UNIT_ROLE 都使用 BUSINESS_UNIT 审批人
            targetType = ApproverTargetType.BUSINESS_UNIT;
        }
        
        if (!approverService.isApprover(approverId, targetType, request.getTargetId())) {
            throw new AdminBusinessException("NOT_APPROVER", "您不是该目标的审批人");
        }
        
        return request;
    }
}
