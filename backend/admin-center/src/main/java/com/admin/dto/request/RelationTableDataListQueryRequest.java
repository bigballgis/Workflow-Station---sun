package com.admin.dto.request;

import com.platform.common.list.ListColumnFilter;

import java.util.List;

/**
 * One page of Relation Table Data: paging, toolbar keyword, column filters and sort.
 * {@link com.admin.list.RelationTableColumnSpec} declares the columns this page can filter and sort.
 */
public record RelationTableDataListQueryRequest(
        int page,
        int size,
        String search,
        List<ListColumnFilter> filters,
        String sortField,
        String sortDirection) {

    public RelationTableDataListQueryRequest {
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
