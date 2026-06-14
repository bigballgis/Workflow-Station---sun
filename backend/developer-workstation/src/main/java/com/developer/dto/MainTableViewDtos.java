package com.developer.dto;

import jakarta.validation.constraints.NotBlank;
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
            Boolean systemField
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
            @NotBlank String viewName
    ) {}

    public record UpdateMainTableViewRequest(
            String viewName,
            List<Map<String, Object>> sortConfig,
            Map<String, Object> filterConfig,
            List<MainTableViewFieldDTO> fields
    ) {}
}
