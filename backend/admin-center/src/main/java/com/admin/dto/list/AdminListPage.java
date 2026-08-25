package com.admin.dto.list;

import com.platform.common.list.ListColumnMeta;
import java.util.List;

/**
 * One page of a shared-list query: the column declaration the header renders from, the rows of
 * this page, and — when the caller grouped — counts over the whole matching set.
 */
public record AdminListPage<T>(
        List<ListColumnMeta> columns,
        List<T> content,
        List<AdminListGroup> groups,
        int page,
        int size,
        long totalElements) {

    public AdminListPage {
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("columns declaration is required");
        }
        columns = List.copyOf(columns);
        content = content == null ? List.of() : List.copyOf(content);
        groups = groups == null ? List.of() : List.copyOf(groups);
    }
}
