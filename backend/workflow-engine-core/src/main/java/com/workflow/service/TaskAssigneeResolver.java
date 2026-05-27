package com.workflow.service;

import com.workflow.client.AdminCenterClient;
import com.workflow.enums.AssigneeType;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Task assignee resolver. For the convergence model, see
 * {@code .kiro/docs/assignee-type-convergence.md}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskAssigneeResolver {

    private final AdminCenterClient adminCenterClient;

    @Data
    @Builder
    public static class ResolveResult {
        private String assignee;
        private List<String> candidateUsers;
        private boolean requiresClaim;
        private AssigneeType assigneeType;
        private String errorMessage;
    }

    /**
     * @param anchorUserId anchor user (initiator or LAST resolution result);
     *        may be null for PROCESS_INITIATOR / BU_ROLE
     * @param anchorActiveBusinessUnitId process variable activeBusinessUnitId,
     *        needed for HIERARCHY resolution in multi-BU scenarios
     */
    public ResolveResult resolve(String assigneeTypeCode, String roleId, String businessUnitId,
                                 String initiatorId, String anchorUserId, String anchorActiveBusinessUnitId) {
        AssigneeType assigneeType = AssigneeType.fromCode(assigneeTypeCode);
        if (assigneeType == null) {
            log.warn("Unknown or deprecated assignee type: {}", assigneeTypeCode);
            return ResolveResult.builder()
                    .errorMessage("Unknown or deprecated assignee type: " + assigneeTypeCode)
                    .build();
        }
        if (assigneeType.isListenerOnly()) {
            return ResolveResult.builder()
                    .assigneeType(assigneeType)
                    .errorMessage("Assignee type " + assigneeType + " is resolved in TaskAssignmentListener")
                    .build();
        }
        return resolve(assigneeType, roleId, businessUnitId, initiatorId, anchorUserId, anchorActiveBusinessUnitId);
    }

    public ResolveResult resolve(String assigneeTypeCode, String roleId, String businessUnitId,
                                 String initiatorId, String anchorUserId) {
        return resolve(assigneeTypeCode, roleId, businessUnitId, initiatorId, anchorUserId, null);
    }

    public ResolveResult resolve(AssigneeType assigneeType, String roleId, String businessUnitId,
                               String initiatorId, String anchorUserId) {
        return resolve(assigneeType, roleId, businessUnitId, initiatorId, anchorUserId, null);
    }

    public ResolveResult resolve(AssigneeType assigneeType, String roleId, String businessUnitId,
                               String initiatorId, String anchorUserId, String anchorActiveBusinessUnitId) {
        log.info("Resolving assignee: type={}, roleId={}, businessUnitId={}, initiator={}, anchorUser={}, anchorActiveBu={}",
                assigneeType, roleId, businessUnitId, initiatorId, anchorUserId, anchorActiveBusinessUnitId);

        String validationError = validateParameters(assigneeType, roleId, businessUnitId, initiatorId, anchorUserId);
        if (validationError != null) {
            return ResolveResult.builder()
                    .assigneeType(assigneeType)
                    .errorMessage(validationError)
                    .build();
        }

        try {
            return switch (assigneeType) {
                case PROCESS_INITIATOR -> resolveProcessInitiator(initiatorId);
                case ENTITY_MANAGER -> resolveEntityManager(anchorUserId);
                case FUNCTIONAL_MANAGER -> resolveFunctionalManager(anchorUserId);
                case HIERARCHY_ROLE -> resolveHierarchyRole(anchorUserId, roleId, anchorActiveBusinessUnitId);
                case BU_ROLE -> resolveBuRole(businessUnitId, roleId);
                default -> ResolveResult.builder()
                        .assigneeType(assigneeType)
                        .errorMessage("Unsupported assignee type in resolver: " + assigneeType)
                        .build();
            };
        } catch (Exception e) {
            log.error("Failed to resolve assignee: type={}, error={}", assigneeType, e.getMessage());
            return ResolveResult.builder()
                    .assigneeType(assigneeType)
                    .errorMessage("Failed to resolve assignee: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Apply 0/1/multi-user rules to user ID list resolved from process variables
     * (consistent with BU_ROLE / HIERARCHY).
     */
    public ResolveResult resolveFromUserIdList(AssigneeType assigneeType, List<String> userIds) {
        if (assigneeType != AssigneeType.ASSIGNEE_FROM_VARIABLE) {
            return ResolveResult.builder()
                    .assigneeType(assigneeType)
                    .errorMessage("resolveFromUserIdList only for ASSIGNEE_FROM_VARIABLE")
                    .build();
        }
        List<String> ids = userIds == null ? List.of() : userIds.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return ResolveResult.builder()
                    .assigneeType(assigneeType)
                    .errorMessage("ASSIGNEE_FROM_VARIABLE: no user IDs resolved")
                    .build();
        }
        if (ids.size() == 1) {
            return ResolveResult.builder()
                    .assignee(ids.get(0))
                    .requiresClaim(false)
                    .assigneeType(assigneeType)
                    .build();
        }
        return ResolveResult.builder()
                .candidateUsers(new ArrayList<>(ids))
                .requiresClaim(true)
                .assigneeType(assigneeType)
                .build();
    }

    private String validateParameters(AssigneeType assigneeType, String roleId, String businessUnitId,
                                      String initiatorId, String anchorUserId) {
        if (assigneeType.requiresRoleId() && (roleId == null || roleId.isEmpty())) {
            return "Assignee type " + assigneeType.getName() + " requires a role ID";
        }
        if (assigneeType.requiresBusinessUnitId() && (businessUnitId == null || businessUnitId.isEmpty())) {
            return "Assignee type " + assigneeType.getName() + " requires a business unit ID";
        }
        if (assigneeType == AssigneeType.PROCESS_INITIATOR && (initiatorId == null || initiatorId.isEmpty())) {
            return "PROCESS_INITIATOR requires process initiator ID";
        }
        if (assigneeType.requiresAnchorUserId() && (anchorUserId == null || anchorUserId.isEmpty())) {
            return "Assignee type " + assigneeType.getName() + " requires anchor user ID";
        }
        return null;
    }

    private ResolveResult resolveProcessInitiator(String initiatorId) {
        return ResolveResult.builder()
                .assignee(initiatorId)
                .assigneeType(AssigneeType.PROCESS_INITIATOR)
                .requiresClaim(false)
                .build();
    }

    private ResolveResult resolveFunctionalManager(String anchorUserId) {
        Map<String, Object> userInfo = adminCenterClient.getUserInfo(anchorUserId);
        if (userInfo == null) {
            return failedManager(AssigneeType.FUNCTIONAL_MANAGER, "Cannot get user info: " + anchorUserId);
        }
        String managerId = (String) userInfo.get("functionManagerId");
        if (managerId == null || managerId.isEmpty()) {
            return failedManager(AssigneeType.FUNCTIONAL_MANAGER, "User has no function manager: " + anchorUserId);
        }
        return ResolveResult.builder()
                .assignee(managerId)
                .assigneeType(AssigneeType.FUNCTIONAL_MANAGER)
                .requiresClaim(false)
                .build();
    }

    private ResolveResult resolveEntityManager(String anchorUserId) {
        Map<String, Object> userInfo = adminCenterClient.getUserInfo(anchorUserId);
        if (userInfo == null) {
            return failedManager(AssigneeType.ENTITY_MANAGER, "Cannot get user info: " + anchorUserId);
        }
        String managerId = (String) userInfo.get("entityManagerId");
        if (managerId == null || managerId.isEmpty()) {
            return failedManager(AssigneeType.ENTITY_MANAGER, "User has no entity manager: " + anchorUserId);
        }
        return ResolveResult.builder()
                .assignee(managerId)
                .assigneeType(AssigneeType.ENTITY_MANAGER)
                .requiresClaim(false)
                .build();
    }

    private static ResolveResult failedManager(AssigneeType type, String msg) {
        return ResolveResult.builder()
                .assigneeType(type)
                .requiresClaim(false)
                .errorMessage(msg)
                .build();
    }

    private ResolveResult resolveHierarchyRole(String anchorUserId, String roleId, String anchorActiveBusinessUnitId) {
        String startBu = adminCenterClient.getUserBusinessUnitId(anchorUserId, anchorActiveBusinessUnitId);
        if (startBu == null || startBu.isEmpty()) {
            return failedPool(AssigneeType.HIERARCHY_ROLE,
                    "Anchor user has no resolvable business unit (multi-BU requires process variable activeBusinessUnitId): "
                            + anchorUserId);
        }
        List<String> candidates = adminCenterClient.collectUserIdsForRoleInBusinessUnitHierarchy(startBu, roleId);
        return toPoolResult(AssigneeType.HIERARCHY_ROLE, candidates, "No users with role " + roleId + " in BU hierarchy for " + anchorUserId);
    }

    private ResolveResult resolveBuRole(String businessUnitId, String roleId) {
        if (!adminCenterClient.isEligibleRole(businessUnitId, roleId)) {
            return failedPool(AssigneeType.BU_ROLE,
                    "Role " + roleId + " is not eligible for business unit " + businessUnitId);
        }
        List<String> candidates = adminCenterClient.getUsersByBusinessUnitAndRole(businessUnitId, roleId);
        return toPoolResult(AssigneeType.BU_ROLE, candidates,
                "No users with role " + roleId + " in business unit " + businessUnitId);
    }

    private static ResolveResult failedPool(AssigneeType type, String msg) {
        return ResolveResult.builder()
                .assigneeType(type)
                .errorMessage(msg)
                .build();
    }

    private static ResolveResult toPoolResult(AssigneeType type, List<String> candidates, String emptyMsg) {
        if (candidates == null || candidates.isEmpty()) {
            return ResolveResult.builder()
                    .assigneeType(type)
                    .errorMessage(emptyMsg)
                    .build();
        }
        if (candidates.size() == 1) {
            return ResolveResult.builder()
                    .assignee(candidates.get(0))
                    .assigneeType(type)
                    .requiresClaim(false)
                    .build();
        }
        return ResolveResult.builder()
                .candidateUsers(new ArrayList<>(candidates))
                .assigneeType(type)
                .requiresClaim(true)
                .build();
    }

    /**
     * @deprecated Only retained for backward compatibility with the old three-argument
     * signature; the anchor user is fixed to the initiator.
     */
    @Deprecated
    public ResolveResult resolve(String assigneeTypeCode, String assigneeValue, String initiatorId) {
        AssigneeType assigneeType = AssigneeType.fromCode(assigneeTypeCode);
        if (assigneeType == null) {
            return ResolveResult.builder()
                    .errorMessage("Unknown assignee type: " + assigneeTypeCode)
                    .build();
        }
        String roleId = assigneeType.requiresRoleId() ? assigneeValue : null;
        String businessUnitId = assigneeType.requiresBusinessUnitId() ? assigneeValue : null;
        return resolve(assigneeType, roleId, businessUnitId, initiatorId, initiatorId);
    }
}
