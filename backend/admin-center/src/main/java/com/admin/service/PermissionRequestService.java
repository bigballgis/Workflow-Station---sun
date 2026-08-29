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
import com.platform.messaging.support.NotificationDispatchHelper;
import com.platform.security.entity.User;
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
    private final NotificationDispatchHelper notificationDispatchHelper;
    
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
            throw new AdminBusinessException("NO_APPROVER", "No approver configured for this virtual group, cannot apply");
        }
        
        // 检查是否存在待审批的申请
        if (permissionRequestRepository.existsByApplicantIdAndTargetIdAndRequestTypeAndStatus(
                applicantId, virtualGroupId, PermissionRequestType.VIRTUAL_GROUP, PermissionRequestStatus.PENDING)) {
            throw new AdminBusinessException("DUPLICATE_REQUEST", "A pending virtual group request already exists");
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
        notifyPermissionRequestSubmitted(request, ApproverTargetType.VIRTUAL_GROUP, virtualGroupId);
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
            throw new AdminBusinessException("NO_APPROVER", "No approver configured for this business unit, cannot apply");
        }
        
        // 验证用户是否有 BU_BOUNDED 角色（通过虚拟组获取）
        List<Role> userBuBoundedRoles = getUserBuBoundedRoles(applicantId);
        if (userBuBoundedRoles.isEmpty()) {
            throw new AdminBusinessException("NO_BU_BOUNDED_ROLE", 
                    "You do not have any BU-Bounded type roles. Please apply to join a virtual group that contains BU-Bounded roles first");
        }
        
        // 检查是否存在待审批的申请
        if (permissionRequestRepository.existsByApplicantIdAndTargetIdAndRequestTypeAndStatus(
                applicantId, businessUnitId, PermissionRequestType.BUSINESS_UNIT, PermissionRequestStatus.PENDING)) {
            throw new AdminBusinessException("DUPLICATE_REQUEST", "A pending business unit request already exists");
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
        notifyPermissionRequestSubmitted(request, ApproverTargetType.BUSINESS_UNIT, businessUnitId);
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
     * Business units a user may apply to: those with an approver, including ones already joined
     * (extra eligible role or MEMBER → LEADER). Same-tier duplicate UBR is rejected on submit.
     */
    public List<com.platform.security.entity.BusinessUnit> getApplicableBusinessUnits(String userId) {
        log.debug("Applicable business units for user {}", userId);
        List<String> businessUnitIdsWithApprover = approverService.getBusinessUnitIdsWithApprover();
        return businessUnitRepository.findAllById(businessUnitIdsWithApprover);
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

        String commentLine = comment != null && !comment.isBlank() ? "审批意见：" + comment : "";
        notificationDispatchHelper.publishToUserAfterCommit(
                request.getApplicantId(),
                "PERMISSION",
                "权限申请已通过",
                String.format("您的申请（编号 %s）已通过审批。%s", request.getId(), commentLine).trim(),
                "/permissions/my-requests",
                "admin-center");
    }
    
    /**
     * 拒绝申请
     */
    @Transactional
    public void reject(String requestId, String approverId, String comment) {
        log.info("Rejecting request: requestId={}, approverId={}", requestId, approverId);
        
        // 验证拒绝时必须提供意见
        if (comment == null || comment.trim().isEmpty()) {
            throw new AdminBusinessException("COMMENT_REQUIRED", "Rejection must include a comment");
        }
        
        PermissionRequest request = getAndValidateRequest(requestId, approverId);
        
        // 更新申请状态
        request.setStatus(PermissionRequestStatus.REJECTED);
        request.setApproverId(approverId);
        request.setApproverComment(comment);
        request.setApprovedAt(Instant.now());
        permissionRequestRepository.save(request);
        
        log.info("Request {} rejected by {}", requestId, approverId);

        notificationDispatchHelper.publishToUserAfterCommit(
                request.getApplicantId(),
                "PERMISSION",
                "权限申请已拒绝",
                String.format("您的申请（编号 %s）未通过审批。原因：%s", request.getId(), comment),
                "/permissions/my-requests",
                "admin-center");
    }
    
    /**
     * 取消申请
     */
    @Transactional
    public void cancel(String requestId, String userId) {
        log.info("Cancelling request: requestId={}, userId={}", requestId, userId);
        
        PermissionRequest request = permissionRequestRepository.findById(requestId)
                .orElseThrow(() -> new AdminBusinessException("REQUEST_NOT_FOUND", "Request not found"));
        
        // 验证是申请人本人
        if (!request.getApplicantId().equals(userId)) {
            throw new AdminBusinessException("NOT_APPLICANT", "Only the applicant can cancel their own request");
        }
        
        // 验证申请状态
        if (request.getStatus() != PermissionRequestStatus.PENDING) {
            throw new AdminBusinessException("INVALID_STATUS", "Only pending requests can be cancelled");
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
                .orElseThrow(() -> new AdminBusinessException("REQUEST_NOT_FOUND", "Request not found"));
    }
    
    /**
     * 获取并验证申请
     */
    private PermissionRequest getAndValidateRequest(String requestId, String approverId) {
        PermissionRequest request = permissionRequestRepository.findById(requestId)
                .orElseThrow(() -> new AdminBusinessException("REQUEST_NOT_FOUND", "Request not found"));
        
        // 验证申请状态
        if (request.getStatus() != PermissionRequestStatus.PENDING) {
            throw new AdminBusinessException("INVALID_STATUS", "Request has already been processed, cannot approve again");
        }
        
        // 验证不能审批自己的申请
        if (request.getApplicantId().equals(approverId)) {
            throw new AdminBusinessException("SELF_APPROVAL", "Cannot approve your own request");
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
            throw new AdminBusinessException("NOT_APPROVER", "You are not an approver for this target");
        }
        
        return request;
    }

    private void notifyPermissionRequestSubmitted(PermissionRequest request, ApproverTargetType targetType, String targetId) {
        var approvers = approverService.getApprovers(targetType, targetId);
        var approverIds = approvers.stream()
                .map(User::getId)
                .filter(id -> id != null && !id.isBlank() && !id.equals(request.getApplicantId()))
                .distinct()
                .toList();
        String typeLabel = request.getRequestType() == PermissionRequestType.VIRTUAL_GROUP ? "虚拟组" : "业务单元";
        String body = String.format("申请人：%s，目标（%s）：%s，申请编号：%s。%s",
                request.getApplicantId(),
                typeLabel,
                request.getTargetId(),
                request.getId(),
                request.getReason() != null && !request.getReason().isBlank() ? "理由：" + request.getReason() : "").trim();
        notificationDispatchHelper.publishToUsersAfterCommit(
                approverIds,
                "PERMISSION",
                "新的权限申请待审批",
                body,
                "/permissions/approvals",
                "admin-center");
        notificationDispatchHelper.publishToUserAfterCommit(
                request.getApplicantId(),
                "PERMISSION",
                "申请已提交",
                String.format("您的权限申请已提交（编号 %s），请等待审批。", request.getId()),
                "/permissions/my-requests",
                "admin-center");
    }
}
