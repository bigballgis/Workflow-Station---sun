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
            List<String> refPrimaryKeyFields
    ) {}

    @Builder
    public record MainTableViewDTO(
            Long id,
            Long functionUnitId,
            Long mainTableId,
            String viewName,
            Boolean isDefault,
            String status,
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
            List<Map<String, Object>> sortConfig,
            Map<String, Object> filterConfig,
            List<MainTableViewFieldDTO> fields
    ) {}
}
