package com.admin.dto.request;

import com.platform.common.list.ListColumnFilter;

import java.util.List;

/**
 * One page of Relation Table Structure. Toolbar {@code functionUnitId} AND with header filters.
 * Blank = all tables; {@code __common__} = tables with no Function Unit link.
 */
public record RelationTableStructureListQueryRequest(
        int page,
        int size,
        String functionUnitId,
        List<ListColumnFilter> filters,
        String sortField,
        String sortDirection) {

    public static final String COMMON_KEY = "__common__";

    public RelationTableStructureListQueryRequest {
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
