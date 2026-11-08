package com.admin.automation;

import com.admin.repository.VirtualGroupRepository;
import com.admin.servicetask.ApWorkspaceResolver;
import com.admin.servicetask.ApWorkspaceResolver.ApWorkspace;
import com.admin.servicetask.config.ServiceTaskProperties;
import com.platform.common.enums.ErrorCode;
import com.platform.common.exception.BusinessException;
import com.platform.security.entity.VirtualGroup;
import com.platform.security.util.SecurityContextUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Workspace runtime identity and the scope frozen when an automation flow is published. */
@Component
public class AutomationConnectionComponent {

    private static final String SYS_ADMIN = "SYS_ADMIN";

    private final AutomationConnectionStore store;
    private final ApWorkspaceResolver workspaces;
    private final ServiceTaskProperties properties;
    private final VirtualGroupRepository groups;

    public AutomationConnectionComponent(AutomationConnectionStore store, ApWorkspaceResolver workspaces,
                                         ServiceTaskProperties properties, VirtualGroupRepository groups) {
        this.store = store;
        this.workspaces = workspaces;
        this.properties = properties;
        this.groups = groups;
    }

    public List<Map<String, Object>> listAccounts(String workspaceId) {
        assertCanBind(groupId(AutomationConnectionRules.workspaceKey(workspaceId)));
        return store.listAccounts().stream().map(this::account).toList();
    }

    @Transactional
    public Map<String, Object> register(String workspaceId, String userId, String displayName, boolean platformDefault) {
        assertCanBind(groupId(AutomationConnectionRules.workspaceKey(workspaceId)));
        if (platformDefault) {
            requireSysAdmin();
        }
        if (userId == null || userId.isBlank() || displayName == null || displayName.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_REQUIRED, "userId and displayName are required");
        }
        store.activeUserLabel(userId.trim()).orElseThrow(() -> new BusinessException(
                ErrorCode.RESOURCE_NOT_FOUND, "User does not exist: " + userId));
        store.saveAccount(userId.trim(), displayName.trim(), platformDefault);
        return account(store.findAccount(userId.trim()).orElseThrow());
    }

    public Map<String, Object> runtimeForCaller(String workspaceId) {
        String groupId = groupId(AutomationConnectionRules.workspaceKey(workspaceId));
        workspaces.resolve(currentUserId(), groupId);
        return resolveIdentity(groupId);
    }

    @Transactional
    public Map<String, Object> bind(String workspaceId, String serviceAccountUserId) {
        String groupId = groupId(AutomationConnectionRules.workspaceKey(workspaceId));
        assertCanBind(groupId);
        AutomationConnectionStore.AccountRow account = store.findAccount(serviceAccountUserId).orElseThrow(
                () -> new BusinessException(ErrorCode.VALIDATION_FIELD_INVALID, "Service account is not registered"));
        store.bind(groupId, account.userId());
        return identity(account, false);
    }

    @Transactional
    public Map<String, Object> unbind(String workspaceId) {
        String groupId = groupId(AutomationConnectionRules.workspaceKey(workspaceId));
        assertCanBind(groupId);
        store.unbind(groupId);
        return identity(requiredDefault(), true);
    }

    public Map<String, Object> readScope(String flowId) {
        AutomationConnectionStore.ScopeRow scope = store.findScope(flowId).orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Published scope was not found"));
        workspaces.resolve(currentUserId(), scope.workspaceId());
        return scopeView(scope);
    }

    @Transactional
    public Map<String, Object> publishScope(String flowId, Long functionUnitId, List<String> catalogIds,
                                            List<String> capabilities, String workspaceId,
                                            String serviceAccountUserId) {
        if (flowId == null || flowId.isBlank() || functionUnitId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_REQUIRED, "flowId and functionUnitId are required");
        }
        String groupId = groupId(AutomationConnectionRules.workspaceKey(workspaceId));
        AutomationConnectionRules.assertCanPublish(workspaces.resolve(currentUserId(), groupId).canWrite());
        if (!store.assignedToGroup(functionUnitId, groupId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Function Unit is not assigned to this workspace");
        }
        String catalogId = AutomationConnectionRules.requireSingleEnabledCatalog(store.enabledCatalogIds(functionUnitId));
        List<String> requestedCatalogs = catalogIds == null || catalogIds.isEmpty()
                ? List.of(catalogId)
                : catalogIds;
        AutomationConnectionRules.assertSingleCatalog(requestedCatalogs, catalogId);
        String stored = String.join(",", AutomationConnectionRules.capabilities(capabilities));
        String accountId = blankToNull(serviceAccountUserId);
        if (accountId != null) {
            AutomationConnectionRules.requireRegistered(store.findAccount(accountId).isPresent());
        }
        store.saveScope(flowId, groupId, functionUnitId, catalogId, stored, accountId);
        return scopeView(store.findScope(flowId).orElseThrow());
    }

    @Transactional
    public Map<String, Object> updateFlowIdentity(String flowId, String workspaceId, String serviceAccountUserId) {
        if (flowId == null || flowId.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_REQUIRED, "flowId is required");
        }
        String groupId = groupId(AutomationConnectionRules.workspaceKey(workspaceId));
        AutomationConnectionRules.assertCanPublish(workspaces.resolve(currentUserId(), groupId).canWrite());
        AutomationConnectionStore.ScopeRow scope = store.findScope(flowId).orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Published scope was not found"));
        if (!groupId.equals(scope.workspaceId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Flow scope belongs to another workspace");
        }
        String accountId = blankToNull(serviceAccountUserId);
        if (accountId != null) {
            AutomationConnectionRules.requireRegistered(store.findAccount(accountId).isPresent());
        }
        store.updateFlowIdentity(flowId, accountId);
        return scopeView(store.findScope(flowId).orElseThrow());
    }

    public Map<String, Object> runtimeForService(String externalProjectId, String flowId) {
        String groupId = groupFromExternal(externalProjectId);
        if (flowId == null || flowId.isBlank()) {
            return resolveIdentity(groupId);
        }
        AutomationConnectionStore.ScopeRow scope = store.findScope(flowId).orElse(null);
        String accountId = AutomationConnectionRules.flowRuntimeAccount(
                scope == null ? null : scope.workspaceId(), groupId,
                scope == null ? null : scope.serviceAccountUserId(),
                store.boundUserId(groupId).orElse(null));
        if (accountId == null || accountId.isBlank()) {
            return identity(requiredDefault(), true);
        }
        Optional<AutomationConnectionStore.AccountRow> account = store.findAccount(accountId);
        if (account.isPresent()) {
            return identity(account.get(), false);
        }
        if (scope != null && accountId.equals(scope.serviceAccountUserId())) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "The flow runtime account is no longer registered");
        }
        return identity(requiredDefault(), true);
    }

    public Map<String, Object> scopeForService(String externalProjectId, String flowId) {
        if (flowId == null || flowId.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_REQUIRED, "flowId is required");
        }
        String groupId = groupFromExternal(externalProjectId);
        AutomationConnectionStore.ScopeRow scope = store.findScope(flowId).orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Published scope was not found"));
        if (!groupId.equals(scope.workspaceId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Flow scope belongs to another workspace");
        }
        return scopeView(scope);
    }

    private void assertCanBind(String groupId) {
        requireKnownGroup(groupId);
        ApWorkspace workspace = workspaces.resolve(currentUserId(), groupId);
        AutomationConnectionRules.assertCanBind(workspace.canWrite(), workspace.publicWorkspace());
    }

    private void requireKnownGroup(String groupId) {
        if (properties.getManaged().getPublicGroupId().equals(groupId)) {
            return;
        }
        VirtualGroup group = groups.findById(groupId).orElseThrow(() -> new BusinessException(
                ErrorCode.FORBIDDEN, "Workspace does not exist"));
        if (!"ACTIVE".equals(group.getStatus()) || !List.of("CUSTOM", "DEVELOPER").contains(group.getType())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Workspace is not an active developer group");
        }
    }

    private String groupFromExternal(String externalProjectId) {
        if (externalProjectId == null || externalProjectId.isBlank()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "externalProjectId is not a workspace");
        }
        ServiceTaskProperties.Managed managed = properties.getManaged();
        if (externalProjectId.equals(managed.getProjectExternalId())) {
            return managed.getPublicGroupId();
        }
        String prefix = managed.getTeamProjectExternalIdPrefix();
        if (!externalProjectId.startsWith(prefix) || externalProjectId.length() == prefix.length()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "externalProjectId is not a workspace");
        }
        String groupId = externalProjectId.substring(prefix.length());
        requireKnownGroup(groupId);
        return groupId;
    }

    private String groupId(String requested) {
        ServiceTaskProperties.Managed managed = properties.getManaged();
        if (requested.equals(managed.getProjectExternalId())) {
            return managed.getPublicGroupId();
        }
        String prefix = managed.getTeamProjectExternalIdPrefix();
        if (requested.startsWith(prefix)) {
            return requested.substring(prefix.length());
        }
        return requested;
    }

    private Map<String, Object> resolveIdentity(String groupId) {
        return store.boundUserId(groupId).flatMap(store::findAccount)
                .map(account -> identity(account, false))
                .orElseGet(() -> identity(requiredDefault(), true));
    }

    private AutomationConnectionStore.AccountRow requiredDefault() {
        return store.platformDefault().orElseThrow(() -> new BusinessException(
                ErrorCode.SERVICE_UNAVAILABLE, "Platform default service account is not configured"));
    }

    private Map<String, Object> account(AutomationConnectionStore.AccountRow row) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", row.userId());
        body.put("displayName", row.displayName());
        body.put("platformDefault", row.platformDefault());
        return body;
    }

    private Map<String, Object> identity(AutomationConnectionStore.AccountRow row, boolean usingDefault) {
        Map<String, Object> body = account(row);
        body.put("usingPlatformDefault", usingDefault);
        return body;
    }

    private Map<String, Object> scopeView(AutomationConnectionStore.ScopeRow scope) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("flowId", scope.flowId());
        body.put("workspaceId", scope.workspaceId());
        body.put("functionUnitId", scope.functionUnitId());
        body.put("catalogIds", List.of(scope.catalogId()));
        body.put("capabilities", Arrays.asList(scope.capabilities().split(",")));
        body.put("serviceAccountUserId", scope.serviceAccountUserId());
        return body;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void requireSysAdmin() {
        if (!sysAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "System administrator is required");
        }
    }

    private boolean sysAdmin() {
        return SecurityContextUtils.isSuperAdmin() || SecurityContextUtils.hasRole(SYS_ADMIN)
                || SecurityContextUtils.hasRole("SUPER_ADMIN");
    }

    private String currentUserId() {
        return SecurityContextUtils.getCurrentUserId().filter(id -> !id.isBlank()).orElseThrow(
                () -> new BusinessException(ErrorCode.UNAUTHORIZED, "Login is required"));
    }
}
