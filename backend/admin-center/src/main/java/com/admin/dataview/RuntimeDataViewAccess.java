package com.admin.dataview;

import com.admin.repository.RoleRepository;
import com.admin.repository.UserRoleRepository;
import com.admin.service.UserBusinessUnitService;
import com.platform.common.functionunit.CatalogSnapshotReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

/** The single policy for Portal views, process-detail grants and view-bound dashboards. */
@Component
@RequiredArgsConstructor
public class RuntimeDataViewAccess {
    private final UserRoleRepository userRoles;
    private final RoleRepository roles;
    private final UserBusinessUnitService businessUnits;

    public record Principal(boolean administrator, Set<String> roleIds, Set<String> buIds) { }

    public Principal principal(String userId) {
        if (userId == null || userId.isBlank()) throw new org.springframework.security.access.AccessDeniedException("User required");
        var active = roles.findAllById(userRoles.findEffectiveViewRoleIdsByUserId(userId)).stream()
                .filter(role -> "ACTIVE".equals(role.getStatus())).toList();
        boolean admin = active.stream().anyMatch(role -> "SYS_ADMIN".equals(role.getCode()));
        Set<String> ids = new HashSet<>();
        active.stream().filter(role -> "BU_BOUNDED".equals(role.getType()) || "BU_UNBOUNDED".equals(role.getType()))
                .forEach(role -> ids.add(role.getId()));
        return new Principal(admin, ids, new HashSet<>(businessUnits.getUserBusinessUnitIds(userId)));
    }

    /** View visibility is independent of the FU's Request in User Portal grant. */
    public boolean allows(Principal principal, Map<String, Object> definition) {
        if (principal.administrator()) return true;
        Set<String> requiredBu = new HashSet<>();
        Set<String> requiredRoles = new HashSet<>();
        if (definition.get("accessRules") == null) return false; // Legacy unconfigured View: admin only.
        for (var rule : CatalogSnapshotReader.objects(definition.get("accessRules"))) {
            String type=String.valueOf(rule.get("targetType")).trim();
            if ("BUSINESS_UNIT".equalsIgnoreCase(type)) requiredBu.add(String.valueOf(rule.get("targetId")));
            if ("ROLE".equalsIgnoreCase(type)) requiredRoles.add(String.valueOf(rule.get("targetId")));
        }
        return !requiredBu.isEmpty() && !requiredRoles.isEmpty()
                && !Collections.disjoint(requiredBu, principal.buIds())
                && !Collections.disjoint(requiredRoles, principal.roleIds());
    }
}
