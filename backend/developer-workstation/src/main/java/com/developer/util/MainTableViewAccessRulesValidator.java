package com.developer.util;

import com.developer.dto.MainTableViewDtos.MainTableViewAccessRuleDTO;
import com.developer.entity.MainTableViewAccess;
import com.developer.enums.MainTableViewAccessTargetType;
import com.developer.exception.DeveloperBusinessException;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * View access rules must be empty (nobody sees the view, System Administrator included) or include both BU and
 * Role dimensions.
 * Used by Save, publish, clone, import/export portability.
 */
public final class MainTableViewAccessRulesValidator {

    public static final String PAIR_ERROR_CODE = "BIZ_VIEW_ACCESS_BU_ROLE_PAIR";
    public static final String PAIR_ERROR_MESSAGE =
            "Business units and roles must both be configured, or both left empty";
    public static final String IMPORT_UNRESOLVED_CODE = "BIZ_VIEW_ACCESS_IMPORT_UNRESOLVED";
    public static final String EXPORT_UNRESOLVED_CODE = "BIZ_VIEW_ACCESS_EXPORT_UNRESOLVED";

    private MainTableViewAccessRulesValidator() {
    }

    public static void validatePairedOrEmpty(List<MainTableViewAccessRuleDTO> rules) {
        if (rules == null || rules.isEmpty()) {
            return;
        }
        Set<String> buIds = new HashSet<>();
        Set<String> roleIds = new HashSet<>();
        for (MainTableViewAccessRuleDTO rule : rules) {
            if (rule.targetType() == null || rule.targetId() == null || rule.targetId().isBlank()) {
                continue;
            }
            String type = rule.targetType().trim().toUpperCase(Locale.ROOT);
            if ("BUSINESS_UNIT".equals(type)) {
                buIds.add(rule.targetId().trim());
            } else if ("ROLE".equals(type)) {
                roleIds.add(rule.targetId().trim());
            }
        }
        assertPairedSets(buIds, roleIds);
    }

    public static void validatePairedOrEmptyEntities(List<MainTableViewAccess> rules) {
        if (rules == null || rules.isEmpty()) {
            return;
        }
        Set<String> buIds = new HashSet<>();
        Set<String> roleIds = new HashSet<>();
        for (MainTableViewAccess rule : rules) {
            if (rule.getTargetId() == null || rule.getTargetId().isBlank()) {
                continue;
            }
            if (rule.getTargetType() == MainTableViewAccessTargetType.BUSINESS_UNIT) {
                buIds.add(rule.getTargetId().trim());
            } else if (rule.getTargetType() == MainTableViewAccessTargetType.ROLE) {
                roleIds.add(rule.getTargetId().trim());
            }
        }
        assertPairedSets(buIds, roleIds);
    }

    private static void assertPairedSets(Set<String> buIds, Set<String> roleIds) {
        if (buIds.isEmpty() && roleIds.isEmpty()) {
            return;
        }
        if (buIds.isEmpty() || roleIds.isEmpty()) {
            throw new DeveloperBusinessException(PAIR_ERROR_CODE, PAIR_ERROR_MESSAGE);
        }
    }

    public static DeveloperBusinessException importUnresolved(String viewName, String targetType, Object targetCode) {
        return new DeveloperBusinessException(
                IMPORT_UNRESOLVED_CODE,
                "View '" + viewName + "': could not resolve access rule "
                        + targetType + " code=" + targetCode
                        + " — ensure BU/Role codes exist in target environment");
    }

    /**
     * Another environment can only map a rule by its BU/Role code, so a rule whose id no longer
     * resolves to a code here would arrive pointing at nothing.
     */
    public static DeveloperBusinessException exportUnresolved(String viewName, String targetType, String targetId) {
        String target = MainTableViewAccessTargetType.ROLE.name().equals(targetType) ? "role" : "business unit";
        return new DeveloperBusinessException(
                EXPORT_UNRESOLVED_CODE,
                "View '" + viewName + "': access rule " + targetType + " id=" + targetId
                        + " does not match any " + target + " with a code in this environment, so it cannot be"
                        + " exported. Reselect the Business Units and Roles of this View in View Design.");
    }
}
