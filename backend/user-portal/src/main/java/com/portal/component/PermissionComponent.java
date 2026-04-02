package com.portal.component;

import com.portal.dto.PermissionRequestDto;
import com.portal.entity.PermissionRequest;
import com.portal.enums.PermissionRequestStatus;
import com.portal.enums.PermissionRequestType;
import com.portal.repository.PermissionRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 权限申请组件
 * 支持角色申请和虚拟组加入申请
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionComponent {

    private final PermissionRequestRepository permissionRequestRepository;
    private final RoleAccessComponent roleAccessComponent;
    private final VirtualGroupAccessComponent virtualGroupAccessComponent;

    // ==================== 新的权限申请方法 ====================

    /**
     * 获取用户可申请的业务角色（排除已拥有的）
     */
    public List<Map<String, Object>> getAvailableRoles(String userId) {
        // 获取所有业务角色
        List<Map<String, Object>> allRoles = roleAccessComponent.getBusinessRoles();
        
        // 获取用户已有的角色ID
        List<Map<String, Object>> userRoles = roleAccessComponent.getUserBusinessRoles(userId);
        Set<String> userRoleIds = userRoles.stream()
                .map(r -> (String) r.get("id"))
                .collect(Collectors.toSet());
        
        // 过滤掉已有的角色
        return allRoles.stream()
                .filter(r -> !userRoleIds.contains(r.get("id")))
                .collect(Collectors.toList());
    }

    /**
     * 获取用户可加入的虚拟组（排除已加入的）
     */
    public List<Map<String, Object>> getAvailableVirtualGroups(String userId) {
        // 获取所有虚拟组
        List<Map<String, Object>> allGroups = virtualGroupAccessComponent.getVirtualGroups();
        
        // 获取用户已加入的虚拟组ID
        List<Map<String, Object>> userGroups = virtualGroupAccessComponent.getUserVirtualGroups(userId);
        Set<String> userGroupIds = userGroups.stream()
                .map(g -> (String) g.get("groupId"))
                .collect(Collectors.toSet());
        
        // 过滤掉已加入的虚拟组
        return allGroups.stream()
                .filter(g -> !userGroupIds.contains(g.get("id")))
                .collect(Collectors.toList());
    }

    /**
     * 获取用户可加入的业务单元（排除已加入的）
     */
    public List<Map<String, Object>> getAvailableBusinessUnits(String userId) {
        // 获取所有业务单元
        List<Map<String, Object>> allBusinessUnits = virtualGroupAccessComponent.getBusinessUnits();
        
        // 获取用户已加入的业务单元ID
        List<Map<String, Object>> userBusinessUnits = virtualGroupAccessComponent.getUserBusinessUnits(userId);
        Set<String> userBuIds = userBusinessUnits.stream()
                .map(bu -> {
                    Object id = bu.get("id");
                    if (id == null) id = bu.get("businessUnitId");
                    return id != null ? id.toString() : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        
        // 过滤掉已加入的业务单元
        return allBusinessUnits.stream()
                .filter(bu -> !userBuIds.contains(bu.get("id")))
                .collect(Collectors.toList());
    }

    /**
     * 业务单元全量目录（扁平列表，供成员管理等场景下拉）
     */
    public List<Map<String, Object>> getBusinessUnitsCatalog() {
        return virtualGroupAccessComponent.getBusinessUnits();
    }

    /**
     * 指定业务单元已绑定的业务角色
     */
    public List<Map<String, Object>> getBusinessUnitRoles(String businessUnitId) {
        return virtualGroupAccessComponent.getBusinessUnitBoundRoles(businessUnitId);
    }

    /**
     * 申请角色分配（自动批准）
     */
    public PermissionRequest requestRoleAssignment(String userId, String roleId, String organizationUnitId, String reason) {
        // 获取角色信息
        Map<String, Object> role = roleAccessComponent.getRoleById(roleId);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在: " + roleId);
        }
        
        // 获取组织单元信息（使用 BusinessUnit API）
        Map<String, Object> orgUnit = virtualGroupAccessComponent.getBusinessUnitById(organizationUnitId);
        if (orgUnit == null) {
            throw new IllegalArgumentException("组织单元不存在: " + organizationUnitId);
        }
        
        // 创建申请记录
        PermissionRequest request = PermissionRequest.builder()
                .applicantId(userId)
                .requestType(PermissionRequestType.ROLE_ASSIGNMENT)
                .roleId(roleId)
                .roleName((String) role.get("name"))
                .organizationUnitId(organizationUnitId)
                .organizationUnitName((String) orgUnit.get("name"))
                .reason(reason)
                .status(PermissionRequestStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        
        // 保存申请记录
        request = permissionRequestRepository.save(request);
        
        // 自动批准：调用 Admin Center API 分配角色
        boolean success = roleAccessComponent.assignRoleToUser(userId, roleId, userId, reason);
        
        if (success) {
            request.setStatus(PermissionRequestStatus.APPROVED);
            request.setApproveTime(LocalDateTime.now());
            request.setApproveComment("系统自动批准");
            log.info("Role assignment auto-approved: user={}, role={}", userId, roleId);
        } else {
            request.setStatus(PermissionRequestStatus.REJECTED);
            request.setApproveTime(LocalDateTime.now());
            request.setApproveComment("角色分配失败");
            log.error("Role assignment failed: user={}, role={}", userId, roleId);
        }
        
        return permissionRequestRepository.save(request);
    }

    /**
     * 申请加入虚拟组（需要审批）
     */
    public PermissionRequest requestVirtualGroupJoin(String userId, String virtualGroupId, String reason) {
        // 获取虚拟组信息
        Map<String, Object> group = virtualGroupAccessComponent.getVirtualGroupById(virtualGroupId);
        if (group == null) {
            throw new IllegalArgumentException("虚拟组不存在: " + virtualGroupId);
        }
        
        // 检查是否已是成员
        if (virtualGroupAccessComponent.isUserInVirtualGroup(userId, virtualGroupId)) {
            throw new IllegalArgumentException("您已是该虚拟组成员");
        }
        
        // 创建申请记录 - 状态为 PENDING，等待审批
        PermissionRequest request = PermissionRequest.builder()
                .applicantId(userId)
                .requestType(PermissionRequestType.VIRTUAL_GROUP_JOIN)
                .virtualGroupId(virtualGroupId)
                .virtualGroupName((String) group.get("name"))
                .reason(reason)
                .status(PermissionRequestStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        
        // 保存申请记录
        request = permissionRequestRepository.save(request);
        log.info("Virtual group join request created: user={}, group={}, requestId={}", userId, virtualGroupId, request.getId());
        
        return request;
    }

    /**
     * 申请加入业务单元（需要审批）
     */
    public PermissionRequest requestBusinessUnitJoin(String userId, String businessUnitId, String reason) {
        return requestBusinessUnitJoinWithRole(userId, businessUnitId, null, reason);
    }

    /**
     * 申请加入业务单元，并指定该业务单元下的一条 Eligible Role（与 admin 中 BU 绑定角色一致）
     */
    public PermissionRequest requestBusinessUnitJoinWithRole(String userId, String businessUnitId, String roleId, String reason) {
        // 获取业务单元信息
        Map<String, Object> businessUnit = virtualGroupAccessComponent.getBusinessUnitById(businessUnitId);
        if (businessUnit == null) {
            throw new IllegalArgumentException("业务单元不存在: " + businessUnitId);
        }

        // 检查是否已是成员
        if (virtualGroupAccessComponent.isUserInBusinessUnit(userId, businessUnitId)) {
            throw new IllegalArgumentException("您已是该业务单元成员");
        }

        String roleName = null;
        if (roleId != null && !roleId.isBlank()) {
            List<Map<String, Object>> bound = virtualGroupAccessComponent.getBusinessUnitBoundRoles(businessUnitId);
            boolean eligible = bound.stream().anyMatch(r -> roleId.equals(String.valueOf(r.get("id"))));
            if (!eligible) {
                throw new IllegalArgumentException("所选角色不在该业务单元的可申请角色列表中");
            }
            roleName = bound.stream()
                    .filter(r -> roleId.equals(String.valueOf(r.get("id"))))
                    .map(r -> Objects.toString(r.get("name"), null))
                    .findFirst()
                    .orElse(null);
        }

        // 创建申请记录 - 状态为 PENDING，等待审批
        PermissionRequest request = PermissionRequest.builder()
                .applicantId(userId)
                .requestType(PermissionRequestType.BUSINESS_UNIT_JOIN)
                .businessUnitId(businessUnitId)
                .businessUnitName((String) businessUnit.get("name"))
                .roleId(roleId != null && !roleId.isBlank() ? roleId : null)
                .roleName(roleName)
                .reason(reason)
                .status(PermissionRequestStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        // 保存申请记录
        request = permissionRequestRepository.save(request);
        log.info("Business unit join request created: user={}, businessUnit={}, roleId={}, requestId={}",
                userId, businessUnitId, roleId, request.getId());

        return request;
    }

    /**
     * 获取用户当前的角色列表
     */
    public List<Map<String, Object>> getUserCurrentRoles(String userId) {
        return roleAccessComponent.getUserBusinessRoles(userId);
    }

    // ==================== 审批相关方法 ====================

    /**
     * 获取所有待审批的申请（审批人视图）
     * @deprecated 使用 getPendingApprovalsForUser 替代，只返回用户可以审批的申请
     */
    @Deprecated
    public Page<PermissionRequest> getPendingApprovals(Pageable pageable) {
        return permissionRequestRepository.findByStatus(PermissionRequestStatus.PENDING, pageable);
    }
    
    /**
     * 获取用户可以审批的待审批申请
     * 只返回用户作为审批人的VG/BU的加入申请
     */
    public Page<PermissionRequest> getPendingApprovalsForUser(String userId, Pageable pageable) {
        // 获取用户作为审批人的VG和BU ID列表
        List<String> approverVgIds = virtualGroupAccessComponent.getApproverVirtualGroupIds(userId);
        List<String> approverBuIds = virtualGroupAccessComponent.getApproverBusinessUnitIds(userId);
        
        // 获取所有待审批的申请
        Page<PermissionRequest> allPending = permissionRequestRepository.findByStatus(PermissionRequestStatus.PENDING, pageable);
        
        // 过滤出用户可以审批的申请
        List<PermissionRequest> filteredList = allPending.getContent().stream()
                .filter(request -> {
                    if (request.getRequestType() == PermissionRequestType.VIRTUAL_GROUP_JOIN) {
                        return approverVgIds.contains(request.getVirtualGroupId());
                    } else if (request.getRequestType() == PermissionRequestType.BUSINESS_UNIT_JOIN) {
                        return approverBuIds.contains(request.getBusinessUnitId());
                    }
                    return false;
                })
                .collect(Collectors.toList());

        enrichApplicantFields(filteredList);
        return new PageImpl<>(filteredList, pageable, filteredList.size());
    }

    /**
     * 获取用户的审批历史（已处理的申请）
     * 只返回用户作为审批人处理过的申请
     */
    public Page<PermissionRequest> getApprovalHistoryForUser(String userId, Pageable pageable) {
        // 获取用户作为审批人的VG和BU ID列表
        List<String> approverVgIds = virtualGroupAccessComponent.getApproverVirtualGroupIds(userId);
        List<String> approverBuIds = virtualGroupAccessComponent.getApproverBusinessUnitIds(userId);
        
        // 获取所有已处理的申请（APPROVED / REJECTED / CANCELLED）
        List<PermissionRequestStatus> processedStatuses = Arrays.asList(
                PermissionRequestStatus.APPROVED, 
                PermissionRequestStatus.REJECTED,
                PermissionRequestStatus.CANCELLED
        );
        Page<PermissionRequest> allProcessed = permissionRequestRepository.findByStatusIn(processedStatuses, pageable);
        
        // 过滤出用户可以看到的审批历史（用户是审批人的VG/BU的申请，或者用户亲自审批的）
        List<PermissionRequest> filteredList = allProcessed.getContent().stream()
                .filter(request -> {
                    // 如果是用户亲自审批的，显示
                    if (userId.equals(request.getApproverId())) {
                        return true;
                    }
                    // 如果用户是该VG/BU的审批人，也显示
                    if (request.getRequestType() == PermissionRequestType.VIRTUAL_GROUP_JOIN) {
                        return approverVgIds.contains(request.getVirtualGroupId());
                    } else if (request.getRequestType() == PermissionRequestType.BUSINESS_UNIT_JOIN) {
                        return approverBuIds.contains(request.getBusinessUnitId());
                    }
                    return false;
                })
                .collect(Collectors.toList());

        enrichApplicantFields(filteredList);
        return new PageImpl<>(filteredList, pageable, filteredList.size());
    }

    /**
     * 批准申请
     */
    public PermissionRequest approveRequest(Long requestId, String approverId, String comment) {
        Optional<PermissionRequest> requestOpt = permissionRequestRepository.findById(requestId);
        if (requestOpt.isEmpty()) {
            throw new IllegalArgumentException("申请不存在: " + requestId);
        }
        
        PermissionRequest request = requestOpt.get();
        if (request.getStatus() != PermissionRequestStatus.PENDING) {
            throw new IllegalArgumentException("申请已处理，无法重复审批");
        }
        
        // 验证审批人权限
        if (!canApproveRequest(approverId, request)) {
            throw new IllegalArgumentException("您没有权限审批此申请");
        }
        
        // 执行实际的权限分配
        boolean success = false;
        String errorMessage = null;
        
        try {
            if (request.getRequestType() == PermissionRequestType.VIRTUAL_GROUP_JOIN) {
                success = virtualGroupAccessComponent.addUserToVirtualGroup(
                        request.getApplicantId(), 
                        request.getVirtualGroupId(), 
                        "审批通过: " + (comment != null ? comment : "")
                );
                if (!success) {
                    errorMessage = "添加用户到虚拟组失败";
                }
            } else if (request.getRequestType() == PermissionRequestType.BUSINESS_UNIT_JOIN) {
                success = virtualGroupAccessComponent.addUserToBusinessUnit(
                        request.getApplicantId(), 
                        request.getBusinessUnitId(), 
                        "审批通过: " + (comment != null ? comment : "")
                );
                if (!success) {
                    errorMessage = "添加用户到业务单元失败";
                } else if (request.getRoleId() != null && !request.getRoleId().isBlank()) {
                    boolean roleOk = virtualGroupAccessComponent.assignUserBusinessUnitRole(
                            request.getApplicantId(),
                            request.getBusinessUnitId(),
                            request.getRoleId());
                    if (!roleOk) {
                        errorMessage = "用户已加入业务单元，但分配业务单元角色失败";
                    } else {
                        // Role Members page在 admin-center 里是按“角色绑定的虚拟组成员”展示。
                        // 为了让 BU join + 选角后的结果在该页面可见，同步把用户加入该虚拟组。
                        String boundVirtualGroupId = virtualGroupAccessComponent
                                .getVirtualGroupIdByBoundRoleId(request.getRoleId());
                        if (boundVirtualGroupId != null && !boundVirtualGroupId.isBlank()) {
                            virtualGroupAccessComponent.addUserToVirtualGroup(
                                    request.getApplicantId(),
                                    boundVirtualGroupId,
                                    "审批通过: " + (comment != null ? comment : "")
                            );
                        }
                    }
                }
            } else if (request.getRequestType() == PermissionRequestType.ROLE_ASSIGNMENT) {
                success = roleAccessComponent.assignRoleToUser(
                        request.getApplicantId(),
                        request.getRoleId(),
                        approverId,
                        "审批通过: " + (comment != null ? comment : "")
                );
                if (!success) {
                    errorMessage = "分配角色失败";
                }
            } else {
                errorMessage = "不支持的申请类型: " + request.getRequestType();
            }
        } catch (Exception e) {
            log.error("Failed to execute approval action for request {}: {}", requestId, e.getMessage());
            errorMessage = "执行审批操作失败: " + e.getMessage();
        }
        
        if (success) {
            request.setStatus(PermissionRequestStatus.APPROVED);
            request.setApproverId(approverId);
            request.setApproveTime(LocalDateTime.now());
            request.setApproveComment(comment != null ? comment : "审批通过");
            log.info("Request {} approved by {}", requestId, approverId);
        } else {
            // 如果执行失败，仍然标记为已批准但添加错误信息
            request.setStatus(PermissionRequestStatus.APPROVED);
            request.setApproverId(approverId);
            request.setApproveTime(LocalDateTime.now());
            request.setApproveComment((comment != null ? comment + " - " : "") + "注意: " + errorMessage);
            log.warn("Request {} approved but action failed: {}", requestId, errorMessage);
        }
        
        return permissionRequestRepository.save(request);
    }

    /**
     * 拒绝申请
     */
    public PermissionRequest rejectRequest(Long requestId, String approverId, String comment) {
        Optional<PermissionRequest> requestOpt = permissionRequestRepository.findById(requestId);
        if (requestOpt.isEmpty()) {
            throw new IllegalArgumentException("申请不存在: " + requestId);
        }
        
        PermissionRequest request = requestOpt.get();
        if (request.getStatus() != PermissionRequestStatus.PENDING) {
            throw new IllegalArgumentException("申请已处理，无法重复审批");
        }
        
        // 验证审批人权限
        if (!canApproveRequest(approverId, request)) {
            throw new IllegalArgumentException("您没有权限审批此申请");
        }
        
        if (comment == null || comment.trim().isEmpty()) {
            throw new IllegalArgumentException("拒绝申请必须填写原因");
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
        }
        // 角色分配暂时不需要审批（自动批准）
        return false;
    }

    /**
     * 获取申请人信息
     */
    public Map<String, Object> getApplicantInfo(String applicantId) {
        try {
            return roleAccessComponent.getUserById(applicantId);
        } catch (Exception e) {
            log.error("Failed to get applicant info for {}: {}", applicantId, e.getMessage());
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("id", applicantId);
            fallback.put("username", applicantId);
            return fallback;
        }
    }

    /**
     * 为审批列表填充申请人登录名等信息（供前端展示，避免只显示 applicantId UUID）
     */
    private void enrichApplicantFields(List<PermissionRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        LinkedHashSet<String> ids = requests.stream()
                .map(PermissionRequest::getApplicantId)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, Map<String, Object>> userById = new HashMap<>();
        for (String id : ids) {
            Map<String, Object> info = roleAccessComponent.getUserById(id);
            if (info != null) {
                userById.put(id, info);
            }
        }
        for (PermissionRequest r : requests) {
            Map<String, Object> info = userById.get(r.getApplicantId());
            if (info == null) {
                continue;
            }
            String username = nonBlankString(info.get("username"));
            String fullName = nonBlankString(info.get("fullName"));
            String displayName = nonBlankString(info.get("displayName"));
            // 优先登录名；缺失时用姓名兜底，避免仍显示 UUID
            String shown = firstNonBlank(username, fullName, displayName);
            if (shown != null) {
                r.setApplicantUsername(shown);
            }
        }
    }

    private static String nonBlankString(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static String firstNonBlank(String... parts) {
        if (parts == null) {
            return null;
        }
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                return p;
            }
        }
        return null;
    }

    /**
     * 获取用户当前的虚拟组成员身份
     */
    public List<Map<String, Object>> getUserCurrentVirtualGroups(String userId) {
        return virtualGroupAccessComponent.getUserVirtualGroups(userId);
    }

    // ==================== 旧的方法（保留兼容） ====================

    /**
     * 获取用户当前权限
     * @deprecated 使用 getUserCurrentRoles 和 getUserCurrentVirtualGroups 替代
     */
    @Deprecated
    public List<Map<String, Object>> getUserPermissions(String userId) {
        List<Map<String, Object>> permissions = new ArrayList<>();
        
        // 添加角色权限
        List<Map<String, Object>> roles = getUserCurrentRoles(userId);
        for (Map<String, Object> role : roles) {
            Map<String, Object> perm = new HashMap<>();
            perm.put("id", role.get("id"));
            perm.put("name", role.get("name"));
            perm.put("type", "ROLE");
            permissions.add(perm);
        }
        
        // 添加虚拟组权限
        List<Map<String, Object>> groups = getUserCurrentVirtualGroups(userId);
        for (Map<String, Object> group : groups) {
            Map<String, Object> perm = new HashMap<>();
            perm.put("id", group.get("groupId"));
            perm.put("name", group.get("groupName"));
            perm.put("type", "VIRTUAL_GROUP");
            permissions.add(perm);
        }
        
        return permissions;
    }

    /**
     * 提交权限申请
     * @deprecated 使用 requestRoleAssignment 或 requestVirtualGroupJoin 替代
     */
    @Deprecated
    public PermissionRequest submitRequest(String userId, PermissionRequestDto dto) {
        if (dto.getType() == null) {
            throw new IllegalArgumentException("权限类型不能为空");
        }
        if (dto.getPermissions() == null || dto.getPermissions().isEmpty()) {
            throw new IllegalArgumentException("权限范围不能为空");
        }
        if (dto.getReason() == null || dto.getReason().isEmpty()) {
            throw new IllegalArgumentException("申请理由不能为空");
        }

        PermissionRequest request = new PermissionRequest();
        request.setApplicantId(userId);
        request.setRequestType(dto.getType());
        request.setPermissions(dto.getPermissions());
        request.setReason(dto.getReason());
        request.setValidFrom(dto.getValidFrom() != null ? dto.getValidFrom() : LocalDateTime.now());
        request.setValidTo(dto.getValidTo());
        request.setStatus(PermissionRequestStatus.PENDING);
        request.setCreatedAt(LocalDateTime.now());

        return permissionRequestRepository.save(request);
    }

    /**
     * 获取用户的权限申请记录
     */
    public Page<PermissionRequest> getMyRequests(String userId, PermissionRequestStatus status, Pageable pageable) {
        if (status != null) {
            List<PermissionRequest> list = permissionRequestRepository.findByApplicantIdAndStatus(userId, status);
            return new PageImpl<>(list, pageable, list.size());
        }
        return permissionRequestRepository.findByApplicantId(userId, pageable);
    }

    /**
     * 获取申请详情
     */
    public Optional<PermissionRequest> getRequestDetail(Long requestId) {
        return permissionRequestRepository.findById(requestId);
    }

    /**
     * 取消申请
     */
    public boolean cancelRequest(String userId, Long requestId) {
        Optional<PermissionRequest> requestOpt = permissionRequestRepository.findById(requestId);
        if (requestOpt.isEmpty()) {
            return false;
        }
        PermissionRequest request = requestOpt.get();
        if (!request.getApplicantId().equals(userId)) {
            return false;
        }
        if (request.getStatus() != PermissionRequestStatus.PENDING) {
            return false;
        }
        // Keep request record for history view.
        request.setStatus(PermissionRequestStatus.CANCELLED);
        permissionRequestRepository.save(request);
        return true;
    }

    /**
     * 续期申请
     * @deprecated 新的权限模型不需要续期
     */
    @Deprecated
    public PermissionRequest renewPermission(String userId, String permissionId, LocalDateTime newValidTo, String reason) {
        PermissionRequest request = new PermissionRequest();
        request.setApplicantId(userId);
        request.setRequestType(PermissionRequestType.TEMPORARY);
        request.setPermissions(Arrays.asList(permissionId));
        request.setReason("续期申请: " + reason);
        request.setValidFrom(LocalDateTime.now());
        request.setValidTo(newValidTo);
        request.setStatus(PermissionRequestStatus.PENDING);
        request.setCreatedAt(LocalDateTime.now());

        return permissionRequestRepository.save(request);
    }

    /**
     * 检查权限是否即将过期
     * @deprecated 新的权限模型不需要过期检查
     */
    @Deprecated
    public List<Map<String, Object>> getExpiringPermissions(String userId, int daysBeforeExpiry) {
        // 新的权限模型不需要过期检查，返回空列表
        return Collections.emptyList();
    }
}
