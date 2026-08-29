package com.admin.dto.request;

import com.platform.common.list.ListColumnFilter;

import java.util.List;

/**
 * One page of roles. Toolbar {@code tab} (SYSTEM/CUSTOM) and {@code type} AND with header filters.
 */
public record RoleListQueryRequest(
        int page,
        int size,
        String tab,
        String type,
        List<ListColumnFilter> filters,
        String sortField,
        String sortDirection) {

    public RoleListQueryRequest {
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
