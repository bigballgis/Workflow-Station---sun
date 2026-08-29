package com.portal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * One Claim All / Unclaim All slice. {@code excludeTaskIds} are already attempted in this click
 * so the next batch does not retry them (avoids looping on permanent failures).
 */
public record ClaimBatchRequest(
        @Size(max = MAX_EXCLUDE_IDS) List<@NotBlank @Size(max = 128) String> excludeTaskIds) {

    /** 20 slices × {@link com.portal.component.ClaimBatchComponent#BATCH_LIMIT}. */
    public static final int MAX_EXCLUDE_IDS = 2000;

    public ClaimBatchRequest {
        excludeTaskIds = excludeTaskIds == null ? List.of() : List.copyOf(excludeTaskIds);
    }
}
