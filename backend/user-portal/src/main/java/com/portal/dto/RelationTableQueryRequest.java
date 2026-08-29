package com.portal.dto;

import com.platform.common.list.ListColumnFilter;
import java.util.List;

/**
 * One page request for a Relation Table: paging, toolbar keyword, column filters and sort.
 */
public record RelationTableQueryRequest(
        int page,
        int size,
        String search,
        List<ListColumnFilter> filters,
        String sortField,
        String sortDirection) {

    public RelationTableQueryRequest {
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

    public static RelationTableQueryRequest of(
            int page,
            int size,
            String search,
            List<ListColumnFilter> filters,
            String sortField,
            String sortDirection) {
        return new RelationTableQueryRequest(page, size, search, filters, sortField, sortDirection);
    }
}
