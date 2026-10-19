package com.portal.dto;

import com.platform.common.list.ListColumnFilter;
import java.util.List;

/**
 * One page of To Do: paging plus shared-header filters, sort.
 * {@code keyword} is the toolbar search (request id / task name / current step / process name /
 * initiator / description) and ANDs with {@code filters}. Toolbar {@code priorities} compile to the same ENUM bands
 * as column chrome. A non-blank keyword, assignmentTypes, or priorities forces portal fullScan.
 * Engine pushdown applies when chrome is fully Flowable-expressible; otherwise portal
 * fullScans for an exact filtered total (PR #107 path — not ACT_RU JDBC).
 */
public record TodoTaskQueryRequest(
        int page,
        int size,
        List<ListColumnFilter> filters,
        String sortField,
        String sortDirection,
        String keyword,
        List<String> assignmentTypes,
        List<String> priorities,
        String batchGroupKey,
        String batchTaskDefinitionKey) {

    public TodoTaskQueryRequest(int page, int size, List<ListColumnFilter> filters, String sortField,
                                String sortDirection, String keyword, List<String> assignmentTypes,
                                List<String> priorities) {
        this(page, size, filters, sortField, sortDirection, keyword, assignmentTypes, priorities, null, null);
    }

    /** 「可批量操作」任务组下钻：只返回该组(功能单元发布版本 + 节点)下本人可批量办理的任务 */
    public boolean batchGroup() {
        return batchGroupKey != null;
    }

    public TodoTaskQueryRequest {
        batchGroupKey = blankToNull(batchGroupKey);
        batchTaskDefinitionKey = blankToNull(batchTaskDefinitionKey);
        if ((batchGroupKey == null) != (batchTaskDefinitionKey == null)) {
            throw new IllegalArgumentException(
                    "batchGroupKey and batchTaskDefinitionKey must be set together");
        }
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > 200) {
            throw new IllegalArgumentException("size must be between 1 and 200");
        }
        filters = filters == null ? List.of() : List.copyOf(filters);
        if (keyword != null) {
            keyword = keyword.trim();
            if (keyword.isEmpty()) {
                keyword = null;
            }
        }
        assignmentTypes = assignmentTypes == null ? List.of() : List.copyOf(assignmentTypes);
        priorities = priorities == null ? List.of() : List.copyOf(priorities);
        if (sortDirection != null && !"ASC".equalsIgnoreCase(sortDirection)
                && !"DESC".equalsIgnoreCase(sortDirection)) {
            throw new IllegalArgumentException("sortDirection must be ASC or DESC");
        }
        if (sortField != null && sortDirection == null) {
            throw new IllegalArgumentException("sortDirection is required when sortField is set");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
