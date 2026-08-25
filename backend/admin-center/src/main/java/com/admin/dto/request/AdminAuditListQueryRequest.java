package com.admin.dto.request;

import com.platform.common.list.ListColumnFilter;

import java.util.List;

/**
 * One page of Admin Center audit logs. Toolbar fields AND with shared-header filters.
 */
public record AdminAuditListQueryRequest(
        int page,
        int size,
        String action,
        String resourceType,
        String username,
        String result,
        String ipAddress,
        String resourceId,
        String startTime,
        String endTime,
        List<ListColumnFilter> filters,
        String sortField,
        String sortDirection,
        String groupBy) {

    public AdminAuditListQueryRequest {
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
