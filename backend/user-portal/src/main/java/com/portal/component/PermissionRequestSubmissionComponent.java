package com.portal.component;

import com.portal.entity.PermissionRequest;
import com.portal.enums.PermissionRequestStatus;
import com.portal.enums.PermissionRequestType;
import com.portal.repository.PermissionRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 权限申请的创建（角色分配自动批准、虚拟组/业务单元加入、业务单元退出、业务单元角色移除）。
 * 从 {@link PermissionComponent} 拆出，行为与原实现逐字一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionRequestSubmissionComponent {

    private final PermissionRequestRepository permissionRequestRepository;
    private final RoleAccessComponent roleAccessComponent;
    private final VirtualGroupAccessComponent virtualGroupAccessComponent;

    /**
     * 申请角色分配（自动批准）
     */
    public PermissionRequest requestRoleAssignment(String userId, String roleId, String organizationUnitId, String reason) {
        // 获取角色信息
        Map<String, Object> role = roleAccessComponent.getRoleById(roleId);
        if (role == null) {
            throw new IllegalArgumentException("Role does not exist: " + roleId);
        }

        // 获取组织单元信息（使用 BusinessUnit API）
        Map<String, Object> orgUnit = virtualGroupAccessComponent.getBusinessUnitById(organizationUnitId);
        if (orgUnit == null) {
            throw new IllegalArgumentException("Organization unit does not exist: " + organizationUnitId);
        }

        // 创建申请记录
        PermissionRequest request = PermissionRequest.builder()
                .applicantId(userId)
                .submittedByUserId(userId)
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
            request.setApproveComment("System auto-approved");
            log.info("Role assignment auto-approved: user={}, role={}", userId, roleId);
        } else {
            request.setStatus(PermissionRequestStatus.REJECTED);
            request.setApproveTime(LocalDateTime.now());
            request.setApproveComment("Role assignment failed");
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
            throw new IllegalArgumentException("Virtual group does not exist: " + virtualGroupId);
        }

        // 检查是否已是成员
        if (virtualGroupAccessComponent.isUserInVirtualGroup(userId, virtualGroupId)) {
            throw new IllegalArgumentException("You are already a member of this virtual group");
        }

        // 创建申请记录 - 状态为 PENDING，等待审批
        PermissionRequest request = PermissionRequest.builder()
                .applicantId(userId)
                .submittedByUserId(userId)
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
    public PermissionRequest requestBusinessUnitJoin(String submittedByUserId, String beneficiaryUserId,
                                                     String businessUnitId, String reason) {
        return requestBusinessUnitJoinWithRole(submittedByUserId, beneficiaryUserId, businessUnitId, null, reason);
    }

    /**
     * 申请加入业务单元，并指定该业务单元下的一条 Eligible Role（与 admin 中 BU 绑定角色一致）
     *
     * @param submittedByUserId 当前登录用户
     * @param beneficiaryUserId 受益人（为空则与提交人相同）
     */
    public PermissionRequest requestBusinessUnitJoinWithRole(String submittedByUserId, String beneficiaryUserId,
                                                             String businessUnitId, String roleId, String reason) {
        return requestBusinessUnitJoinWithRole(submittedByUserId, beneficiaryUserId, businessUnitId, roleId, reason, null);
    }

    public PermissionRequest requestBusinessUnitJoinWithRole(String submittedByUserId, String beneficiaryUserId,
                                                             String businessUnitId, String roleId, String reason,
                                                             String membershipType) {
        Objects.requireNonNull(submittedByUserId, "submittedByUserId");
        String beneficiary = normalizeUserIdOrDefault(beneficiaryUserId, submittedByUserId);
        assertActiveBeneficiary(beneficiary);
        String tier = com.platform.security.ubr.UbrMembershipType.normalize(membershipType);

        Map<String, Object> businessUnit = virtualGroupAccessComponent.getBusinessUnitById(businessUnitId);
        if (businessUnit == null) {
            throw new IllegalArgumentException("Business unit does not exist: " + businessUnitId);
        }

        String roleName = resolveEligibleRoleName(businessUnitId, roleId);
        assertJoinOrLeaderUpgradeAllowed(beneficiary, businessUnitId, roleId, tier);

        PermissionRequest request = PermissionRequest.builder()
                .applicantId(beneficiary)
                .submittedByUserId(submittedByUserId)
                .requestType(PermissionRequestType.BUSINESS_UNIT_JOIN)
                .businessUnitId(businessUnitId)
                .businessUnitName((String) businessUnit.get("name"))
                .roleId(roleId != null && !roleId.isBlank() ? roleId : null)
                .roleName(roleName)
                .membershipType(tier)
                .reason(reason)
                .status(PermissionRequestStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        request = permissionRequestRepository.save(request);
        log.info("Business unit join request created: beneficiary={}, submittedBy={}, businessUnit={}, roleId={}, membershipType={}, requestId={}",
                beneficiary, submittedByUserId, businessUnitId, roleId, tier, request.getId());
        return request;
    }

    private String resolveEligibleRoleName(String businessUnitId, String roleId) {
        if (roleId == null || roleId.isBlank()) {
            return null;
        }
        List<Map<String, Object>> bound = virtualGroupAccessComponent.getBusinessUnitBoundRoles(businessUnitId);
        boolean eligible = bound.stream().anyMatch(r -> roleId.equals(String.valueOf(r.get("id"))));
        if (!eligible) {
            throw new IllegalArgumentException("Selected role is not in the available roles list for this business unit");
        }
        return bound.stream()
                .filter(r -> roleId.equals(String.valueOf(r.get("id"))))
                .map(r -> Objects.toString(r.get("name"), null))
                .findFirst()
                .orElse(null);
    }

    private void assertJoinOrLeaderUpgradeAllowed(String beneficiary, String businessUnitId, String roleId, String tier) {
        if (!virtualGroupAccessComponent.isUserInBusinessUnit(beneficiary, businessUnitId)) {
            return;
        }
        if (roleId == null || roleId.isBlank()) {
            throw new IllegalArgumentException("This user is already a member of this business unit");
        }
        String current = currentMembershipType(beneficiary, businessUnitId, roleId);
        if (current == null) {
            return;
        }
        if (com.platform.security.ubr.UbrMembershipType.LEADER.equals(tier)
                && com.platform.security.ubr.UbrMembershipType.MEMBER.equals(current)) {
            return;
        }
        throw new IllegalArgumentException("This user already has this role in the business unit");
    }

    private String currentMembershipType(String userId, String businessUnitId, String roleId) {
        return virtualGroupAccessComponent.listUserBusinessUnitRolesInBusinessUnit(userId, businessUnitId).stream()
                .filter(r -> roleId != null && roleId.equals(String.valueOf(r.get("roleId"))))
                .map(r -> Objects.toString(r.get("membershipType"), com.platform.security.ubr.UbrMembershipType.MEMBER))
                .findFirst()
                .orElse(null);
    }

    /**
     * 申请退出业务单元（成员）：审批通过后移除成员及该 BU 下全部 UBR
     */
    public PermissionRequest requestBusinessUnitExit(String submittedByUserId, String beneficiaryUserId,
                                                     String businessUnitId, String reason) {
        Objects.requireNonNull(submittedByUserId, "submittedByUserId");
        String beneficiary = normalizeUserIdOrDefault(beneficiaryUserId, submittedByUserId);
        assertActiveBeneficiary(beneficiary);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Please provide a reason for the request");
        }
        Map<String, Object> businessUnit = virtualGroupAccessComponent.getBusinessUnitById(businessUnitId);
        if (businessUnit == null) {
            throw new IllegalArgumentException("Business unit does not exist: " + businessUnitId);
        }
        if (!virtualGroupAccessComponent.isUserInBusinessUnit(beneficiary, businessUnitId)) {
            throw new IllegalArgumentException("This user is not a member of this business unit and cannot request exit");
        }
        PermissionRequest request = PermissionRequest.builder()
                .applicantId(beneficiary)
                .submittedByUserId(submittedByUserId)
                .requestType(PermissionRequestType.BUSINESS_UNIT_EXIT)
                .businessUnitId(businessUnitId)
                .businessUnitName((String) businessUnit.get("name"))
                .reason(reason)
                .status(PermissionRequestStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        request = permissionRequestRepository.save(request);
        log.info("Business unit exit request created: beneficiary={}, submittedBy={}, bu={}, requestId={}",
                beneficiary, submittedByUserId, businessUnitId, request.getId());
        return request;
    }

    /**
     * 申请移除在指定业务单元下的业务角色（需该业务单元审批人批准后生效）
     */
    public PermissionRequest requestBusinessUnitRoleRemoval(String submittedByUserId, String beneficiaryUserId,
                                                            String businessUnitId, String roleId, String reason) {
        Objects.requireNonNull(submittedByUserId, "submittedByUserId");
        String beneficiary = normalizeUserIdOrDefault(beneficiaryUserId, submittedByUserId);
        assertActiveBeneficiary(beneficiary);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Please provide a reason for the request");
        }
        if (roleId == null || roleId.isBlank()) {
            throw new IllegalArgumentException("Role cannot be empty");
        }
        Map<String, Object> businessUnit = virtualGroupAccessComponent.getBusinessUnitById(businessUnitId);
        if (businessUnit == null) {
            throw new IllegalArgumentException("Business unit does not exist: " + businessUnitId);
        }
        if (!virtualGroupAccessComponent.userHasBusinessUnitRole(beneficiary, businessUnitId, roleId)) {
            throw new IllegalArgumentException("This user does not have this role in this business unit, cannot request removal");
        }
        List<Map<String, Object>> bound = virtualGroupAccessComponent.getBusinessUnitBoundRoles(businessUnitId);
        String roleName = bound.stream()
                .filter(r -> roleId.equals(String.valueOf(r.get("id"))))
                .map(r -> Objects.toString(r.get("name"), null))
                .findFirst()
                .orElse(null);
        if (roleName == null) {
            roleName = virtualGroupAccessComponent.listUserBusinessUnitRolesInBusinessUnit(beneficiary, businessUnitId).stream()
                    .filter(r -> roleId.equals(String.valueOf(r.get("roleId"))))
                    .map(r -> Objects.toString(r.get("roleName"), null))
                    .findFirst()
                    .orElse(null);
        }
        PermissionRequest request = PermissionRequest.builder()
                .applicantId(beneficiary)
                .submittedByUserId(submittedByUserId)
                .requestType(PermissionRequestType.BUSINESS_UNIT_ROLE_REMOVAL)
                .businessUnitId(businessUnitId)
                .businessUnitName((String) businessUnit.get("name"))
                .roleId(roleId)
                .roleName(roleName)
                .reason(reason)
                .status(PermissionRequestStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        request = permissionRequestRepository.save(request);
        log.info("BU role removal request created: beneficiary={}, submittedBy={}, bu={}, roleId={}, requestId={}",
                beneficiary, submittedByUserId, businessUnitId, roleId, request.getId());
        return request;
    }

    private static String normalizeUserIdOrDefault(String beneficiaryUserId, String submittedByUserId) {
        if (beneficiaryUserId == null || beneficiaryUserId.isBlank()) {
            return submittedByUserId;
        }
        return beneficiaryUserId.trim();
    }

    private void assertActiveBeneficiary(String beneficiaryUserId) {
        if (!roleAccessComponent.isActivePortalUser(beneficiaryUserId)) {
            throw new IllegalArgumentException("Beneficiary does not exist or account is not available");
        }
    }
}
