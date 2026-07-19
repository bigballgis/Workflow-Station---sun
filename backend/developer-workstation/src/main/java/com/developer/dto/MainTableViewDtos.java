package com.developer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.List;
import java.util.Map;

public final class MainTableViewDtos {

    private MainTableViewDtos() {}

    @Builder
    public record MainTableViewFieldDTO(
            String fieldName,
            String displayLabel,
            Integer columnWidth,
            Integer sortOrder,
            Boolean visible,
            Boolean systemField,
            // Derived from FieldDefinition at read time (output-only; ignored on update).
            // Drives designer-internal FK navigation and Portal FK drill-down.
            Boolean isPrimaryKey,
            Boolean isForeignKey,
            Long refTableId,
            List<String> refPrimaryKeyFields,
            /** {@code field}, {@code lookup_display}, or {@code fk_display}. */
            String columnType,
            /** For lookup_display / fk_display: source field (widget or FK column). */
            String lookupSourceField,
            /** For lookup_display / fk_display: attribute on the related row. */
            String lookupDisplayField
    ) {}

    @Builder
    public record MainTableViewAccessRuleDTO(
            String targetType,
            String targetId,
            String targetName
    ) {}

    @Builder
    public record MainTableViewDTO(
            Long id,
            Long functionUnitId,
            Long mainTableId,
            String viewName,
            Boolean isDefault,
            String status,
            Boolean restrictToInvolvedUsers,
            List<MainTableViewAccessRuleDTO> accessRules,
            List<Map<String, Object>> sortConfig,
            Map<String, Object> filterConfig,
            List<MainTableViewFieldDTO> fields
    ) {}

    public record CreateMainTableViewRequest(
            @NotBlank String viewName,
            @NotNull Long tableId
    ) {}

    public record UpdateMainTableViewRequest(
            String viewName,
            Boolean restrictToInvolvedUsers,
            List<MainTableViewAccessRuleDTO> accessRules,
            List<Map<String, Object>> sortConfig,
            Map<String, Object> filterConfig,
            List<MainTableViewFieldDTO> fields
    ) {}
}
