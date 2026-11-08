package com.admin.automation;

import com.platform.common.enums.ErrorCode;
import com.platform.common.exception.BusinessException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pure checks for automation identity binding and published flow scope. */
public final class AutomationConnectionRules {

    public static final String ALL_WORKSPACES = "__ALL__";
    public static final Set<String> CAPABILITIES = Set.of("read", "query", "write", "subtable", "start");

    private AutomationConnectionRules() {
    }

    public static String workspaceKey(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank() || ALL_WORKSPACES.equals(workspaceId.trim())) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_INVALID, "workspaceId is required");
        }
        return workspaceId.trim();
    }

    /**
     * @param canWrite same flag as publishing a flow in this workspace
     */
    public static void assertCanBind(boolean canWrite, boolean publicWorkspace) {
        if (canWrite) {
            return;
        }
        if (publicWorkspace) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "Only a system administrator can bind the Public workspace");
        }
        throw new BusinessException(ErrorCode.FORBIDDEN,
                "You cannot change the runtime identity of this workspace");
    }

    public static void assertCanPublish(boolean canWrite) {
        if (!canWrite) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "You cannot publish automation in this workspace");
        }
    }

    public static List<String> capabilities(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_REQUIRED, "capabilities are required");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String capability : requested) {
            if (capability == null || !CAPABILITIES.contains(capability)) {
                throw new BusinessException(ErrorCode.VALIDATION_FIELD_INVALID,
                        "Unknown capability: " + capability);
            }
            normalized.add(capability);
        }
        return new ArrayList<>(normalized);
    }

    public static String requireSingleEnabledCatalog(List<String> enabledCatalogIds) {
        if (enabledCatalogIds != null && enabledCatalogIds.size() > 1) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_INVALID,
                    "This Function Unit has more than one enabled catalog");
        }
        if (enabledCatalogIds == null || enabledCatalogIds.size() != 1
                || enabledCatalogIds.get(0) == null || enabledCatalogIds.get(0).isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_INVALID,
                    "This Function Unit has no enabled catalog");
        }
        return enabledCatalogIds.get(0);
    }

    public static void requireRegistered(boolean registered) {
        if (!registered) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_INVALID, "Service account is not registered");
        }
    }

    /**
     * @param scopeWorkspaceId null when the flow has no published scope
     * @return the flow account, otherwise the workspace account; null means use the platform default
     */
    public static String flowRuntimeAccount(String scopeWorkspaceId, String callerWorkspaceId,
                                            String flowAccountId, String workspaceAccountId) {
        if (scopeWorkspaceId != null && !scopeWorkspaceId.equals(callerWorkspaceId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Flow scope belongs to another workspace");
        }
        if (flowAccountId != null && !flowAccountId.isBlank()) {
            return flowAccountId;
        }
        return workspaceAccountId;
    }

    public static void assertSingleCatalog(List<String> requested, String actualCatalogId) {
        if (actualCatalogId == null || actualCatalogId.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_INVALID,
                    "This Function Unit has no published catalog");
        }
        if (requested == null || requested.size() != 1 || !actualCatalogId.equals(requested.get(0))) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "Scope catalog must be the catalog of the selected Function Unit");
        }
    }
}
