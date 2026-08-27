package com.admin.dto.request;

import com.platform.common.list.ListColumnFilter;

import java.util.List;

/**
 * One page of User Portal audit logs. Toolbar fields AND with shared-header filters.
 * JSON field set matches {@code com.portal.dto.UserPortalAuditListQueryRequest}.
 */
public record UserPortalAuditListQueryRequest(
        int page,
        int size,
        String username,
        String functionUnitCode,
        String changeType,
        String processInstanceId,
        String startTime,
        String endTime,
        List<ListColumnFilter> filters,
        String sortField,
        String sortDirection) {

    public UserPortalAuditListQueryRequest {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > 200) {
            throw new IllegalArgumentException("size must be between 1 and 200");
        }
        filters = filters == null ? List.of() : List.copyOf(filters);
        if (sortDirection != null && !"ASC".equalsIgnoreCase(sortDirection)
                && !"DESC".equalsIgnoreCase(sortDirection)) {
            throw new IllegalArgumentException("sortDirection must be ASC or DESC");
        }
        if (sortField != null && sortDirection == null) {
            throw new IllegalArgumentException("sortDirection is required when sortField is set");
        }
    }
}
