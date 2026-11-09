package com.admin.bi.dto.response;

import lombok.Builder;

/** A MAIN / SUB table of a unit's active release that a published View lists. */
@Builder
public record DataViewTableOptionResponse(
        String tableName,
        String tableDisplayName,
        String tableType) {
}
