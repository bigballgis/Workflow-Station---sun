package com.admin.dto.list;

import com.admin.dto.response.RelationTableResponse;
import com.platform.common.list.ListColumnMeta;

import java.util.List;

/**
 * Structure list page: shared-list payload plus the left-rail Function Unit groups
 * (always the full catalog, independent of the selected rail filter).
 */
public record RelationTableStructureListPage(
        List<ListColumnMeta> columns,
        List<RelationTableResponse> content,
        List<AdminListGroup> groups,
        int page,
        int size,
        long totalElements,
        List<RelationTableFuGroup> functionUnitGroups) {

    public RelationTableStructureListPage {
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("columns declaration is required");
        }
        columns = List.copyOf(columns);
        content = content == null ? List.of() : List.copyOf(content);
        groups = groups == null ? List.of() : List.copyOf(groups);
        functionUnitGroups = functionUnitGroups == null ? List.of() : List.copyOf(functionUnitGroups);
    }
}
