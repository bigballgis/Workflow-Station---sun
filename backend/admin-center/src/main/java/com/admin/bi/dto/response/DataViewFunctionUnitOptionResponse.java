package com.admin.bi.dto.response;

import lombok.Builder;

/** A function unit whose active release publishes at least one View. */
@Builder
public record DataViewFunctionUnitOptionResponse(String code, String name) {
}
