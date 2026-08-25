package com.workflow.dto.request;

import java.util.Date;

/**
 * Optional list criteria pushed from Portal into Flowable {@code TaskQuery}
 * so filtered/sorted pages need not be full-scanned in the portal.
 *
 * <p>{@code taskNameLike} / {@code processDefinitionNameLike} are raw fragments (no {@code %});
 * the engine wraps them. Date bounds are inclusive start-of-day / exclusive next-day style
 * as interpreted by Flowable {@code taskCreatedAfter}/{@code Before} etc.
 */
public record EngineTaskListCriteria(
        String taskNameLike,
        String taskNameExact,
        String taskNameLikeMode,
        Integer priority,
        Integer priorityMin,
        Integer priorityMax,
        Date createdAfter,
        Date createdBefore,
        Date dueAfter,
        Date dueBefore,
        String processDefinitionNameLike,
        String processDefinitionNameExact,
        String sortBy,
        String sortDirection
) {
    public static EngineTaskListCriteria empty() {
        return new EngineTaskListCriteria(
                null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
    }

    public boolean hasAny() {
        return hasFilterFragments()
                || (sortBy != null && !sortBy.isBlank())
                || (sortDirection != null && !sortDirection.isBlank());
    }

    public boolean hasFilterFragments() {
        return (taskNameLike != null && !taskNameLike.isBlank())
                || (taskNameExact != null && !taskNameExact.isBlank())
                || priority != null
                || priorityMin != null
                || priorityMax != null
                || createdAfter != null
                || createdBefore != null
                || dueAfter != null
                || dueBefore != null
                || (processDefinitionNameLike != null && !processDefinitionNameLike.isBlank())
                || (processDefinitionNameExact != null && !processDefinitionNameExact.isBlank());
    }
}
