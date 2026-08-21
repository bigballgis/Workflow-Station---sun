package com.portal.dto;

import lombok.Builder;

/**
 * A function unit the current user may review.
 * Backs the audit menu's visibility and its unit switcher.
 */
@Builder
public record AuditFunctionUnitItem(
        String functionUnitId,
        String functionUnitCode,
        String functionUnitName
) {}
