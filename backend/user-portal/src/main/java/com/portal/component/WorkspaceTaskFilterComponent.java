package com.portal.component;

import com.platform.security.util.SecurityContextUtils;
import com.portal.client.WorkflowEngineClient;
import com.portal.dto.TaskInfo;
import com.portal.service.PortalWorkspaceAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Workspace (active business unit) scoping for task queries: filters FIXED_BU_ROLE task pools
 * against the JWT active BU and restricts candidate virtual groups to the active workspace.
 * Also owns the short-TTL per-user virtual-group membership cache.
 * Extracted from {@link TaskQueryComponent}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkspaceTaskFilterComponent {

    private final WorkflowEngineClient workflowEngineClient;
    private final VirtualGroupAccessComponent virtualGroupAccessComponent;
    private final PortalWorkspaceAuthService portalWorkspaceAuthService;

    /**
     * FIXED_BU_ROLE or BU_ROLE with an explicit businessUnitId in BPMN extensions:
     * the engine merges into taskCandidateUser, unrelated to candidate group filtering.
     * When the JWT contains {@code activeBusinessUnitId}, the pool's BU must match the current workspace.
     * <p>No longer relies on {@link PortalWorkspaceAuthService#listWorkspaceContexts} being non-empty:
     * in some environments, UBR data may be out of sync with the workspace switcher,
     * causing VG-only filtering to misclassify the situation as "non-workspace mode" and skip this filter.</p>
     */
    /**
     * Workspace BU filter applies to role-pool tasks only. Direct assignee/candidate tasks must remain visible
     * (e.g. rollback fallback assigned user-dev while BPMN still marks BU_ROLE + another BU id).
     */
    public List<TaskInfo> filterFixedBuRoleTasksForActiveWorkspace(List<TaskInfo> tasks, String userId) {
        Optional<String> activeBuOpt = SecurityContextUtils.getCurrentActiveBusinessUnitId();
        String portalUsername = SecurityContextUtils.getCurrentUsername().orElse(null);
        if (activeBuOpt.isEmpty()) {
            return tasks;
        }
        String activeBu = normalizeBuId(activeBuOpt.get());
        List<TaskInfo> out = new ArrayList<>();
        for (TaskInfo t : tasks) {
            if (t == null) {
                continue;
            }
            if (isDirectAssigneeForPortalUser(t, userId, portalUsername)
                    || isCandidateForPortalUser(t, userId, portalUsername)) {
                out.add(t);
                continue;
            }
            if (!isWorkspaceScopedBuPoolSemantics(t)) {
                out.add(t);
                continue;
            }
            String fixedBu = resolveFixedBusinessUnitForBpmnTask(t);
            if (fixedBu == null || fixedBu.isBlank()) {
                out.add(t);
                continue;
            }
            if (equalsNormalizedBuId(activeBu, fixedBu)) {
                out.add(t);
            }
        }
        return out;
    }

    private static boolean isDirectAssigneeForPortalUser(TaskInfo t, String userId, String portalUsername) {
        if (t == null || userId == null || userId.isBlank()) {
            return false;
        }
        String assignee = t.getAssignee();
        if (assignee == null || assignee.isBlank()) {
            return false;
        }
        String a = assignee.trim();
        if (userId.trim().equals(a)) {
            return true;
        }
        return portalUsername != null && !portalUsername.isBlank()
                && portalUsername.trim().equalsIgnoreCase(a);
    }

    private static boolean isCandidateForPortalUser(TaskInfo t, String userId, String portalUsername) {
        if (t == null || userId == null || userId.isBlank() || t.getCandidateUserIds() == null) {
            return false;
        }
        for (String cid : t.getCandidateUserIds()) {
            if (cid == null || cid.isBlank()) {
                continue;
            }
            String c = cid.trim();
            if (userId.trim().equals(c)) {
                return true;
            }
            if (portalUsername != null && !portalUsername.isBlank()
                    && portalUsername.trim().equalsIgnoreCase(c)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeBuId(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim();
    }

    /**
     * FIXED_BU_ROLE, or BU_ROLE with an explicit businessUnitId in BPMN extensions
     * (aligned with engine {@code isWorkspaceScopedBuPoolSemantics}).
     * <p>BPMN extensions only; does not read process variables (to avoid stale cross-node variable spillover).</p>
     */
    private boolean isWorkspaceScopedBuPoolSemantics(TaskInfo t) {
        String bpmn = t.getBpmnAssigneeType();
        if (bpmn != null) {
            String u = bpmn.trim().toUpperCase(java.util.Locale.ROOT);
            if ("FIXED_BU_ROLE".equals(u)) {
                return true;
            }
            if ("BU_ROLE".equals(u) && t.getBpmnBusinessUnitId() != null && !t.getBpmnBusinessUnitId().isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static boolean equalsNormalizedBuId(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        String x = a.trim();
        String y = b.trim();
        if (x.equals(y)) {
            return true;
        }
        try {
            if (x.matches("^-?\\d+$") && y.matches("^-?\\d+$")) {
                return Long.parseLong(x) == Long.parseLong(y);
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        return false;
    }

    /**
     * Fixed business unit: BPMN extension {@code bpmnBusinessUnitId} only
     * (consistent with workflow-engine task list filtering semantics; does not use process variables).
     */
    private String resolveFixedBusinessUnitForBpmnTask(TaskInfo t) {
        String bu = t.getBpmnBusinessUnitId();
        if (bu != null && !bu.isBlank()) {
            return bu.trim();
        }
        return null;
    }

    /**
     * In workspace context: keep only virtual groups where the current user has a UBR
     * for the bound role within the active business unit, preventing users with multiple BUs
     * from seeing candidate group tasks in other BU workspaces (engine user-permissions
     * returns all virtualGroupIds).
     */
    public List<String> filterVirtualGroupsForActiveWorkspace(String userId, List<String> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return groupIds == null ? Collections.emptyList() : groupIds;
        }
        Optional<String> activeBu = SecurityContextUtils.getCurrentActiveBusinessUnitId();
        if (activeBu.isEmpty()) {
            return groupIds;
        }
        if (portalWorkspaceAuthService.listWorkspaceContexts(userId).isEmpty()) {
            return groupIds;
        }
        List<String> kept = new ArrayList<>();
        for (String gid : groupIds) {
            Optional<String> boundRoleId = virtualGroupAccessComponent.getBoundRoleIdForVirtualGroup(gid);
            if (boundRoleId.isEmpty()) {
                log.debug("Workspace VG filter: group {} has no bound role; excluded from candidate-group query", gid);
                continue;
            }
            if (portalWorkspaceAuthService.hasContext(userId, activeBu.get(), boundRoleId.get())) {
                kept.add(gid);
            }
        }
        return kept;
    }

    private static final long VIRTUAL_GROUPS_CACHE_TTL_MS = 30_000L;
    private final Map<String, VirtualGroupsCacheEntry> virtualGroupsCache = new ConcurrentHashMap<>();

    private record VirtualGroupsCacheEntry(List<String> groupIds, long timestampMs) {}

    /**
     * A user's virtual-group membership changes rarely, but every To Do refresh resolves it via a
     * portal→engine→admin-center triple-hop ({@link WorkflowEngineClient#getUserTaskPermissions}) costing
     * ~0.3-1s. Cache the result per user for a short TTL so repeated list/statistics queries reuse it; an
     * admin membership change becomes effective within the TTL.
     */
    public List<String> getUserVirtualGroups(String userId) {
        if (userId == null || userId.isBlank()) {
            return Collections.emptyList();
        }
        VirtualGroupsCacheEntry hit = virtualGroupsCache.get(userId);
        if (hit != null && System.currentTimeMillis() - hit.timestampMs() < VIRTUAL_GROUPS_CACHE_TTL_MS) {
            return hit.groupIds();
        }
        List<String> resolved = fetchUserVirtualGroups(userId);
        virtualGroupsCache.put(userId, new VirtualGroupsCacheEntry(resolved, System.currentTimeMillis()));
        return resolved;
    }

    /**
     * Get virtual groups the user belongs to.
     * Retrieved via workflow-engine-core calling admin-center.
     */
    @SuppressWarnings("unchecked")
    private List<String> fetchUserVirtualGroups(String userId) {
        try {
            Optional<Map<String, Object>> result = workflowEngineClient.getUserTaskPermissions(userId);
            if (result.isPresent()) {
                Map<String, Object> data = result.get();
                List<String> groupIds = (List<String>) data.get("virtualGroupIds");
                if (groupIds != null && !groupIds.isEmpty()) {
                    return groupIds;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get user virtual groups from workflow engine: {}", e.getMessage());
        }
        // Return empty list; do not use mock data
        return Collections.emptyList();
    }
}
