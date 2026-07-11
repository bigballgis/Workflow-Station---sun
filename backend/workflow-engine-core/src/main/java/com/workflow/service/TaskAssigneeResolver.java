package com.workflow.service;

import com.workflow.client.AdminCenterClient;
import com.workflow.enums.AssigneeType;
import com.workflow.exception.AdminCenterUnavailableException;
import com.workflow.util.AssigneeRoleIdsSupport;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
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
        /**
         * true = 解析失败源于 admin-center 传输故障（结果未知，可等服务恢复后自动补分派）；
         * false + errorMessage != null = 配置/数据错误（如角色无准入、无候选人，重试无用，需人工修配置）。
         */
        private boolean infraFailure;
    }

    /**
     * @param anchorUserId anchor user (initiator or LAST resolution result);
     *        may be null for PROCESS_INITIATOR / BU_ROLE
     * @param anchorActiveBusinessUnitId process variable activeBusinessUnitId,
     *        needed for HIERARCHY resolution in multi-BU scenarios
     */
    public ResolveResult resolve(String assigneeTypeCode, String roleId, String businessUnitId,
                                 String initiatorId, String anchorUserId, String anchorActiveBusinessUnitId) {
        return resolveWithRoleIds(assigneeTypeCode,
                AssigneeRoleIdsSupport.parseRoleIds(null, roleId),
                businessUnitId, initiatorId, anchorUserId, anchorActiveBusinessUnitId);
    }

    public ResolveResult resolveWithRoleIds(String assigneeTypeCode, List<String> roleIds, String businessUnitId,
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
        return resolve(assigneeType, roleIds, businessUnitId, initiatorId, anchorUserId, anchorActiveBusinessUnitId);
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
        return resolve(assigneeType,
                AssigneeRoleIdsSupport.parseRoleIds(null, roleId),
                businessUnitId, initiatorId, anchorUserId, anchorActiveBusinessUnitId);
    }

    public ResolveResult resolve(AssigneeType assigneeType, List<String> roleIds, String businessUnitId,
                               String initiatorId, String anchorUserId, String anchorActiveBusinessUnitId) {
        log.info("Resolving assignee: type={}, roleIds={}, businessUnitId={}, initiator={}, anchorUser={}, anchorActiveBu={}",
                assigneeType, roleIds, businessUnitId, initiatorId, anchorUserId, anchorActiveBusinessUnitId);

        String validationError = validateParameters(assigneeType, roleIds, businessUnitId, initiatorId, anchorUserId);
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
                case HIERARCHY_ROLE -> resolveHierarchyRoles(anchorUserId, roleIds, anchorActiveBusinessUnitId);
                case BU_ROLE -> resolveBuRoles(businessUnitId, roleIds);
                default -> ResolveResult.builder()
                        .assigneeType(assigneeType)
                        .errorMessage("Unsupported assignee type in resolver: " + assigneeType)
                        .build();
            };
        } catch (AdminCenterUnavailableException e) {
            log.error("admin-center unavailable while resolving assignee: type={}, error={}",
                    assigneeType, e.getMessage());
            return ResolveResult.builder()
                    .assigneeType(assigneeType)
                    .errorMessage("admin-center unavailable: " + e.getMessage())
                    .infraFailure(true)
                    .build();
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

    private String validateParameters(AssigneeType assigneeType, List<String> roleIds, String businessUnitId,
                                      String initiatorId, String anchorUserId) {
        if (assigneeType.requiresRoleId() && (roleIds == null || roleIds.isEmpty())) {
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

    private ResolveResult resolveHierarchyRoles(String anchorUserId, List<String> roleIds,
                                                String anchorActiveBusinessUnitId) {
        String startBu = adminCenterClient.getUserBusinessUnitId(anchorUserId, anchorActiveBusinessUnitId);
        if (startBu == null || startBu.isEmpty()) {
            return failedPool(AssigneeType.HIERARCHY_ROLE,
                    "Anchor user has no resolvable business unit (multi-BU requires process variable activeBusinessUnitId): "
                            + anchorUserId);
        }
        LinkedHashSet<String> allCandidates = new LinkedHashSet<>();
        for (String roleId : roleIds) {
            if (roleId == null || roleId.isBlank()) {
                continue;
            }
            List<String> chunk = adminCenterClient.collectUserIdsForRoleInBusinessUnitHierarchy(
                    startBu, roleId.trim());
            if (chunk != null) {
                for (String uid : chunk) {
                    if (uid != null && !uid.isBlank()) {
                        allCandidates.add(uid.trim());
                    }
                }
            }
        }
        String roleSummary = String.join(", ", roleIds);
        return toPoolResult(AssigneeType.HIERARCHY_ROLE, new ArrayList<>(allCandidates),
                "No users with roles [" + roleSummary + "] in BU hierarchy for " + anchorUserId);
    }

    private ResolveResult resolveBuRoles(String businessUnitId, List<String> roleIds) {
        LinkedHashSet<String> allCandidates = new LinkedHashSet<>();
        for (String roleId : roleIds) {
            if (roleId == null || roleId.isBlank()) {
                continue;
            }
            String rid = roleId.trim();
            if (!adminCenterClient.isEligibleRole(businessUnitId, rid)) {
                return failedPool(AssigneeType.BU_ROLE,
                        "Role " + rid + " is not eligible for business unit " + businessUnitId);
            }
            List<String> candidates = adminCenterClient.getUsersByBusinessUnitAndRole(businessUnitId, rid);
            if (candidates != null) {
                for (String uid : candidates) {
                    if (uid != null && !uid.isBlank()) {
                        allCandidates.add(uid.trim());
                    }
                }
            }
        }
        String roleSummary = String.join(", ", roleIds);
        return toPoolResult(AssigneeType.BU_ROLE, new ArrayList<>(allCandidates),
                "No users with roles [" + roleSummary + "] in business unit " + businessUnitId);
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
