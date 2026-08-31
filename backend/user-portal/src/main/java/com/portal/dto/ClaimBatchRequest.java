package com.portal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * One Claim All / Unclaim All / selected-row slice. {@code excludeTaskIds} are already attempted
 * in this click so the next batch does not retry them. {@code includeTaskIds}, when non-empty,
 * limits the slice to those ids (To Do checkbox selection); empty means the whole To Do queue.
 */
public record ClaimBatchRequest(
        @Size(max = MAX_EXCLUDE_IDS) List<@NotBlank @Size(max = 128) String> excludeTaskIds,
        @Size(max = MAX_INCLUDE_IDS) List<@NotBlank @Size(max = 128) String> includeTaskIds) {

    /** 20 slices × {@link com.portal.component.ClaimBatchComponent#BATCH_LIMIT}. */
    public static final int MAX_EXCLUDE_IDS = 2000;

    /** Same cap as exclude: one To Do page is far smaller; search-selection still fits. */
    public static final int MAX_INCLUDE_IDS = 2000;

    public ClaimBatchRequest {
        excludeTaskIds = excludeTaskIds == null ? List.of() : List.copyOf(excludeTaskIds);
        includeTaskIds = includeTaskIds == null ? List.of() : List.copyOf(includeTaskIds);
    }

    public ClaimBatchRequest(List<String> excludeTaskIds) {
        this(excludeTaskIds, List.of());
    }
}
