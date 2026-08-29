package com.portal.component;

import com.portal.entity.PermissionRequest;
import com.portal.enums.PermissionRequestStatus;
import com.portal.enums.PermissionRequestType;
import com.portal.repository.PermissionRequestRepository;
import com.platform.common.i18n.I18nService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 权限申请审批：待审批/历史查询、批准/拒绝执行、审批人判定。
 * 安全敏感路径，从 {@link PermissionComponent} 拆出，判定逻辑与原实现逐字一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionApprovalComponent {

    /** 门户「待我审批」中的业务单元类申请类型 */
    private static final List<PermissionRequestType> BU_APPROVER_REQUEST_TYPES = List.of(
            PermissionRequestType.BUSINESS_UNIT_JOIN,
            PermissionRequestType.BUSINESS_UNIT_ROLE_REMOVAL,
            PermissionRequestType.BUSINESS_UNIT_EXIT);

    private final PermissionRequestRepository permissionRequestRepository;
    private final RoleAccessComponent roleAccessComponent;
    private final VirtualGroupAccessComponent virtualGroupAccessComponent;
    private final I18nService i18nService;
    private final PermissionRequestEnrichmentComponent enrichmentComponent;

    /**
     * 获取所有待审批的申请（审批人视图）
     * @deprecated 使用 getPendingApprovalsForUser 替代，只返回用户可以审批的申请
     */
    @Deprecated
    public Page<PermissionRequest> getPendingApprovals(Pageable pageable) {
        return permissionRequestRepository.findByStatus(PermissionRequestStatus.PENDING, pageable);
    }

    /**
     * 获取用户可以审批的待审批申请：业务单元相关（加入/移除角色/退出）+ 虚拟组加入（若该用户为对应 VG 审批人）。
     */
    public Page<PermissionRequest> getPendingApprovalsForUser(String userId, Pageable pageable) {
        List<String> buIds = Optional.ofNullable(virtualGroupAccessComponent.getApproverBusinessUnitIds(userId))
                .orElseGet(Collections::emptyList);
        List<String> vgIds = Optional.ofNullable(virtualGroupAccessComponent.getApproverVirtualGroupIds(userId))
                .orElseGet(Collections::emptyList);
        boolean hasBu = !buIds.isEmpty();
        boolean hasVg = !vgIds.isEmpty();
        if (!hasBu && !hasVg) {
            return Page.empty(pageable);
        }
        Page<PermissionRequest> page;
        if (hasBu && hasVg) {
            page = permissionRequestRepository.findPendingForBuOrVirtualGroupApprovers(
                    PermissionRequestStatus.PENDING,
                    BU_APPROVER_REQUEST_TYPES,
                    buIds,
                    PermissionRequestType.VIRTUAL_GROUP_JOIN,
                    vgIds,
                    pageable);
        } else if (hasBu) {
            page = permissionRequestRepository.findPendingForBusinessUnitApprovers(
                    PermissionRequestStatus.PENDING,
                    BU_APPROVER_REQUEST_TYPES,
                    buIds,
                    pageable);
        } else {
            page = permissionRequestRepository.findPendingForVirtualGroupJoinApprovers(
                    PermissionRequestStatus.PENDING,
                    PermissionRequestType.VIRTUAL_GROUP_JOIN,
                    vgIds,
                    pageable);
        }
        enrichmentComponent.enrichDisplayFields(page.getContent());
        return page;
    }

    /**
     * 获取当前用户作为审批人处理过的记录（批准/拒绝；不含他人代批的同 BU 记录）。
     */
    public Page<PermissionRequest> getApprovalHistoryForUser(String userId, Pageable pageable) {
        List<PermissionRequestStatus> processedStatuses = Arrays.asList(
                PermissionRequestStatus.APPROVED,
                PermissionRequestStatus.REJECTED,
                PermissionRequestStatus.CANCELLED);
        Page<PermissionRequest> page = permissionRequestRepository.findProcessedHistoryByApproverId(
                userId, processedStatuses, pageable);
        enrichmentComponent.enrichDisplayFields(page.getContent());
        return page;
    }

    /**
     * 批准申请
     */
    @Transactional
    public PermissionRequest approveRequest(Long requestId, String approverId, String comment) {
        Optional<PermissionRequest> requestOpt = permissionRequestRepository.findById(requestId);
        if (requestOpt.isEmpty()) {
            throw new IllegalArgumentException("Request does not exist: " + requestId);
        }

        PermissionRequest request = requestOpt.get();
        if (request.getStatus() != PermissionRequestStatus.PENDING) {
            throw new IllegalArgumentException("Request has already been processed, cannot approve again");
        }

        assertApproverNotSameAsApplicant(approverId, request);

        // 验证审批人权限
        if (!canApproveRequest(approverId, request)) {
            throw new IllegalArgumentException("You do not have permission to approve this request");
        }

        // 执行实际的权限分配
        boolean success = false;
        String errorMessage = null;

        try {
            if (request.getRequestType() == PermissionRequestType.VIRTUAL_GROUP_JOIN) {
                success = virtualGroupAccessComponent.addUserToVirtualGroup(
                        request.getApplicantId(),
                        request.getVirtualGroupId(),
                        "Approved: " + (comment != null ? comment : "")
                );
                if (!success) {
                    errorMessage = "Failed to add user to virtual group";
                }
            } else if (request.getRequestType() == PermissionRequestType.BUSINESS_UNIT_JOIN) {
                boolean alreadyInBu = virtualGroupAccessComponent.isUserInBusinessUnit(
                        request.getApplicantId(), request.getBusinessUnitId());
                if (alreadyInBu) {
                    success = true;
                } else {
                    success = virtualGroupAccessComponent.addUserToBusinessUnit(
                            request.getApplicantId(),
                            request.getBusinessUnitId(),
                            "Approved: " + (comment != null ? comment : "")
                    );
                }
                if (!success) {
                    errorMessage = "Failed to add user to business unit";
                } else if (request.getRoleId() != null && !request.getRoleId().isBlank()) {
                    boolean roleOk = virtualGroupAccessComponent.assignUserBusinessUnitRole(
                            request.getApplicantId(),
                            request.getBusinessUnitId(),
                            request.getRoleId(),
                            request.getMembershipType());
                    if (!roleOk) {
                        success = false;
                        errorMessage = "User joined business unit, but failed to assign business unit role";
                        if (!alreadyInBu) {
                            try {
                                virtualGroupAccessComponent.exitBusinessUnit(
                                        request.getApplicantId(), request.getBusinessUnitId());
                            } catch (Exception rollbackEx) {
                                log.error("Failed to roll back BU membership after role assignment failure for request {}: {}",
                                        requestId, rollbackEx.getMessage());
                                errorMessage = errorMessage + " (and automatic membership revocation failed, please contact administrator)";
                            }
                        }
                    } else {
                        // Role Members page在 admin-center 里是按“角色绑定的虚拟组成员”展示。
                        // 为了让 BU join + 选角后的结果在该页面可见，同步把用户加入该虚拟组。
                        String boundVirtualGroupId = virtualGroupAccessComponent
                                .getVirtualGroupIdByBoundRoleId(request.getRoleId());
                        if (boundVirtualGroupId != null && !boundVirtualGroupId.isBlank()) {
                            virtualGroupAccessComponent.addUserToVirtualGroup(
                                    request.getApplicantId(),
                                    boundVirtualGroupId,
                                    "Approved: " + (comment != null ? comment : "")
                            );
                        }
                    }
                }
            } else if (request.getRequestType() == PermissionRequestType.ROLE_ASSIGNMENT) {
                success = roleAccessComponent.assignRoleToUser(
                        request.getApplicantId(),
                        request.getRoleId(),
                        approverId,
                        "Approved: " + (comment != null ? comment : "")
                );
                if (!success) {
                    errorMessage = "Failed to assign role";
                }
            } else if (request.getRequestType() == PermissionRequestType.BUSINESS_UNIT_ROLE_REMOVAL) {
                success = virtualGroupAccessComponent.removeUserBusinessUnitRole(
                        request.getApplicantId(),
                        request.getBusinessUnitId(),
                        request.getRoleId());
                if (!success) {
                    errorMessage = "Failed to remove business unit role";
                } else {
                    List<Map<String, Object>> remaining = virtualGroupAccessComponent
                            .listUserBusinessUnitRolesInBusinessUnit(
                                    request.getApplicantId(),
                                    request.getBusinessUnitId());
                    if (remaining.isEmpty()) {
                        boolean exited = virtualGroupAccessComponent.exitBusinessUnit(
                                request.getApplicantId(),
                                request.getBusinessUnitId());
                        if (!exited) {
                            success = false;
                            errorMessage = "Last business unit role removed, but automatic exit from business unit failed";
                        } else {
                            log.info("User {} left business unit {} after last BU role removed (request {})",
                                    request.getApplicantId(), request.getBusinessUnitId(), requestId);
                        }
                    }
                    // 加入 BU 角色时曾同步加入绑定虚拟组（供 admin 角色页「Role Members」展示）；全局无该角色分配时从 VG 移除
                    virtualGroupAccessComponent.removeFromBoundVirtualGroupIfNoBuRoleAssignmentRemaining(
                            request.getApplicantId(),
                            request.getRoleId());
                }
            } else if (request.getRequestType() == PermissionRequestType.BUSINESS_UNIT_EXIT) {
                success = virtualGroupAccessComponent.exitBusinessUnit(
                        request.getApplicantId(),
                        request.getBusinessUnitId());
                if (!success) {
                    errorMessage = "Failed to exit business unit";
                }
            } else {
                errorMessage = "Unsupported request type: " + request.getRequestType();
            }
        } catch (Exception e) {
            log.error("Failed to execute approval action for request {}: {}", requestId, e.getMessage());
            errorMessage = "Approval execution failed: " + e.getMessage();
        }

        if (!success) {
            String detail = errorMessage != null ? errorMessage : "Unknown error";
            log.warn("Request {} approval execution failed, leaving PENDING: {}", requestId, detail);
            throw new IllegalStateException(detail);
        }

        request.setStatus(PermissionRequestStatus.APPROVED);
        request.setApproverId(approverId);
        request.setApproveTime(LocalDateTime.now());
        request.setApproveComment(comment != null ? comment : "Approved");
        log.info("Request {} approved by {}", requestId, approverId);
        return permissionRequestRepository.save(request);
    }

    /**
     * 拒绝申请
     */
    @Transactional
    public PermissionRequest rejectRequest(Long requestId, String approverId, String comment) {
        Optional<PermissionRequest> requestOpt = permissionRequestRepository.findById(requestId);
        if (requestOpt.isEmpty()) {
            throw new IllegalArgumentException("Request does not exist: " + requestId);
        }

        PermissionRequest request = requestOpt.get();
        if (request.getStatus() != PermissionRequestStatus.PENDING) {
            throw new IllegalArgumentException("Request has already been processed, cannot approve again");
        }

        assertApproverNotSameAsApplicant(approverId, request);

        // 验证审批人权限
        if (!canApproveRequest(approverId, request)) {
            throw new IllegalArgumentException("You do not have permission to approve this request");
        }

        if (comment == null || comment.trim().isEmpty()) {
            throw new IllegalArgumentException("Rejection must include a reason");
        }

        request.setStatus(PermissionRequestStatus.REJECTED);
        request.setApproverId(approverId);
        request.setApproveTime(LocalDateTime.now());
        request.setApproveComment(comment);

        log.info("Request {} rejected by {}: {}", requestId, approverId, comment);

        return permissionRequestRepository.save(request);
    }

    /**
     * 检查用户是否有审批权限（是否是任何VG或BU的审批人）
     */
    public boolean isApprover(String userId) {
        return virtualGroupAccessComponent.isAnyApprover(userId);
    }

    /**
     * 检查用户是否可以审批特定的申请
     */
    public boolean canApproveRequest(String userId, PermissionRequest request) {
        if (request.getRequestType() == PermissionRequestType.VIRTUAL_GROUP_JOIN) {
            return virtualGroupAccessComponent.isApproverForVirtualGroup(userId, request.getVirtualGroupId());
        } else if (request.getRequestType() == PermissionRequestType.BUSINESS_UNIT_JOIN) {
            return virtualGroupAccessComponent.isApproverForBusinessUnit(userId, request.getBusinessUnitId());
        } else if (request.getRequestType() == PermissionRequestType.BUSINESS_UNIT_ROLE_REMOVAL) {
            return virtualGroupAccessComponent.isApproverForBusinessUnit(userId, request.getBusinessUnitId());
        } else if (request.getRequestType() == PermissionRequestType.BUSINESS_UNIT_EXIT) {
            return virtualGroupAccessComponent.isApproverForBusinessUnit(userId, request.getBusinessUnitId());
        }
        // 角色分配暂时不需要审批（自动批准）
        return false;
    }

    /** 防止受益人与审批人为同一人时自批自申请 */
    private void assertApproverNotSameAsApplicant(String approverId, PermissionRequest request) {
        if (approverId != null && request.getApplicantId() != null
                && approverId.equals(request.getApplicantId())) {
            throw new IllegalArgumentException(i18nService.getMessage("portal.cannot_approve_own_request"));
        }
    }
}
