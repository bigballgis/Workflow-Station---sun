package com.admin.dto.request;

import com.platform.common.list.ListColumnFilter;

import java.util.List;

/**
 * One page of the Admin User list. Toolbar {@code keyword} / {@code status} AND with the
 * shared-header filters so the search card and a column filter never disagree.
 */
public record UserListQueryRequest(
        int page,
        int size,
        String keyword,
        String status,
        List<ListColumnFilter> filters,
        String sortField,
        String sortDirection,
        String groupBy) {

    public UserListQueryRequest {
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
