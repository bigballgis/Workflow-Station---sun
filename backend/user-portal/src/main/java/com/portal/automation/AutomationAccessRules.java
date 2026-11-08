package com.portal.automation;

import com.platform.common.enums.ErrorCode;
import com.platform.common.exception.BusinessException;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Runtime checks for one Function Unit data call. Missing scope is a refusal, not full access. */
public final class AutomationAccessRules {

    public static final int MAX_PAGE_SIZE = 50;
    private static final Set<String> ACTOR_KEYS = Set.of("actor", "triggeredBy", "userId", "startUserId");

    private AutomationAccessRules() {
    }

    public static void requireRunContext(String externalProjectId, String flowId) {
        if (externalProjectId == null || externalProjectId.isBlank() || flowId == null || flowId.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_REQUIRED,
                    "externalProjectId and flowId are required");
        }
    }

    public static void rejectActor(Map<String, ?> body) {
        if (body == null) {
            return;
        }
        for (String key : ACTOR_KEYS) {
            if (body.containsKey(key) && body.get(key) != null && !String.valueOf(body.get(key)).isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_FIELD_INVALID,
                        "The caller cannot choose the actor");
            }
        }
    }

    public static void requireCapability(Set<String> capabilities, String capability) {
        if (capabilities == null || !capabilities.contains(capability)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "This flow is not allowed to " + capability);
        }
    }

    public static void requireCatalog(String allowedCatalogId, String targetCatalogId) {
        if (allowedCatalogId == null || !allowedCatalogId.equals(targetCatalogId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "This flow cannot access that Function Unit");
        }
    }

    public static int pageSize(Integer size) {
        int resolved = size == null ? MAX_PAGE_SIZE : size;
        if (resolved < 1 || resolved > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_SIZE_EXCEEDED, "size must be from 1 to 50");
        }
        return resolved;
    }

    public static void requireFilters(List<Map<String, Object>> filters, Set<String> whitelist) {
        if (filters == null || filters.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_REQUIRED, "Query requires at least one filter");
        }
        for (Map<String, Object> filter : filters) {
            Object field = filter == null ? null : filter.get("field");
            if (!(field instanceof String name) || name.isBlank() || whitelist == null || !whitelist.contains(name)) {
                throw new BusinessException(ErrorCode.VALIDATION_FIELD_INVALID, "Filter field is not on the main table");
            }
            if (filter.get("value") == null) {
                throw new BusinessException(ErrorCode.VALIDATION_FIELD_REQUIRED, "Filter value is required");
            }
        }
    }
}
