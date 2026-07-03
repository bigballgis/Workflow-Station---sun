package com.portal.component;

import com.portal.repository.UserBusinessUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves whether a portal user can see a main-table view based on BU/Role access rules.
 * System administrators ({@code SYS_ADMIN}) bypass all view-level restrictions.
 */
@Component
@RequiredArgsConstructor
public class MainTableViewAccessResolver {

    public static final String SYS_ADMIN_ROLE_CODE = FunctionUnitAccessComponent.SYS_ADMIN_ROLE_CODE;

    private final FunctionUnitAccessComponent functionUnitAccessComponent;
    private final UserBusinessUnitRepository userBusinessUnitRepository;

    public record AccessRule(String targetType, String targetId) {}

    public boolean isSystemAdministrator(String userId) {
        return functionUnitAccessComponent.isSystemAdministrator(userId);
    }

    /**
     * BU and Role must be configured as a pair. When both are empty, only
     * {@link #SYS_ADMIN_ROLE_CODE} can see the view. Partial config (BU-only or Role-only)
     * is treated as not visible to non-admin users.
     */
    public boolean canUserSeeView(String userId, List<AccessRule> rules) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        if (isSystemAdministrator(userId)) {
            return true;
        }

        Set<String> requiredBuIds = extractTargetIds(rules, "BUSINESS_UNIT");
        Set<String> requiredRoleIds = extractTargetIds(rules, "ROLE");

        if (requiredBuIds.isEmpty() && requiredRoleIds.isEmpty()) {
            return false;
        }
        if (requiredBuIds.isEmpty() || requiredRoleIds.isEmpty()) {
            return false;
        }

        boolean buOk = !Collections.disjoint(requiredBuIds, getUserBusinessUnitIds(userId));
        boolean roleOk = !Collections.disjoint(requiredRoleIds, functionUnitAccessComponent.getUserBusinessRoleIds(userId));
        return buOk && roleOk;
    }

    private Set<String> extractTargetIds(List<AccessRule> rules, String targetType) {
        if (rules == null || rules.isEmpty()) {
            return Set.of();
        }
        return rules.stream()
                .filter(r -> targetType.equalsIgnoreCase(r.targetType()))
                .map(AccessRule::targetId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
    }

    public List<AccessRule> parseAccessRules(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(row -> new AccessRule(
                        stringVal(row.get("target_type")),
                        stringVal(row.get("target_id"))))
                .filter(r -> r.targetType() != null && r.targetId() != null)
                .toList();
    }

    private Set<String> getUserBusinessUnitIds(String userId) {
        return userBusinessUnitRepository.findByUserId(userId).stream()
                .map(ub -> ub.getBusinessUnitId())
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toCollection(HashSet::new));
    }

    private static String stringVal(Object value) {
        return value != null ? String.valueOf(value) : null;
    }
}
