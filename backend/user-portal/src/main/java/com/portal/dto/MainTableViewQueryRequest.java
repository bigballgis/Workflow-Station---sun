package com.portal.dto;

import com.platform.common.list.ListColumnFilter;
import java.util.List;

/**
 * One page request for a Main Table View: paging, the toolbar keyword, and the column filters,
 * sort the shared list header produces. All of it is answered by the database, so
 * the page the caller receives and the total it is told about describe the same set of rows.
 *
 * <p>{@code rowKey} is the list row's own identity (not a keyword). The view-detail page uses it
 * to load that one row; it is ignored when blank.
 */
public record MainTableViewQueryRequest(
        int page,
        int size,
        String search,
        List<ListColumnFilter> filters,
        String sortField,
        String sortDirection,
        String rowKey) {

    public MainTableViewQueryRequest {
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
        rowKey = rowKey == null || rowKey.isBlank() ? null : rowKey.trim();
    }
}
