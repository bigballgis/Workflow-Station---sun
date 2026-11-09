package com.portal.component;

import com.platform.security.util.SecurityContextUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves whether a portal user can see a main-table view based on BU/Role access rules.
 *
 * <p>Judged against the signed-in user's <em>current</em> workspace pair only — the session's
 * {@code activeBusinessUnitId} + {@code activeRoleId}, which every session refresh re-checks against
 * {@code sys_user_business_unit_roles} — never against the user's BUs and roles combined (platform
 * access model §7.2): holding BU1+R1 and BU2+R2 does not match a rule BU1+R2. No role bypasses the
 * rules, System Administrator included (D-05).
 */
@Component
public class MainTableViewAccessResolver {

    public record AccessRule(String targetType, String targetId) {}

    /**
     * BU and Role must be configured as a pair. When both are empty nobody can see the view; partial
     * config (BU-only or Role-only) is not visible to anyone either. With both configured, the
     * current BU must be one of the rule BUs and the current role one of the rule roles (rules store
     * ids, as Developer Workstation saves and imports them). Without a current pair — permission
     * self-service mode, or {@code userId} is not the signed-in user — nothing is visible.
     */
    public boolean canUserSeeView(String userId, List<AccessRule> rules) {
        if (userId == null || userId.isBlank()) {
            return false;
        }

        Set<String> requiredBuIds = extractTargetIds(rules, "BUSINESS_UNIT");
        Set<String> requiredRoleIds = extractTargetIds(rules, "ROLE");

        if (requiredBuIds.isEmpty() || requiredRoleIds.isEmpty()) {
            return false;
        }
        // The workspace pair in the session belongs to the signed-in user; no one else has one here.
        if (!userId.equals(SecurityContextUtils.getCurrentUserId().orElse(null))) {
            return false;
        }
        Optional<String> activeBu = SecurityContextUtils.getCurrentActiveBusinessUnitId();
        Optional<String> activeRole = SecurityContextUtils.getCurrentActiveRoleId();
        return activeBu.isPresent() && activeRole.isPresent()
                && requiredBuIds.contains(activeBu.get().trim())
                && requiredRoleIds.contains(activeRole.get().trim());
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

    private static String stringVal(Object value) {
        return value != null ? String.valueOf(value) : null;
    }
}
