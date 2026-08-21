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
            /** DETAIL form opened when a row of this view is clicked; null = no detail page. */
            Long detailFormId,
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
            Long detailFormId,
            List<MainTableViewAccessRuleDTO> accessRules,
            List<Map<String, Object>> sortConfig,
            Map<String, Object> filterConfig,
            List<MainTableViewFieldDTO> fields
    ) {}

    /**
     * Sets only a view's detail form. Separate from {@link UpdateMainTableViewRequest} because that
     * one is a whole-design save and resets the view to DRAFT — which would pull a published view
     * out of the portal as a side effect of picking a form.
     *
     * @param detailFormId form to open on row click; null clears the detail page.
     */
    public record UpdateMainTableViewDetailFormRequest(
            Long detailFormId
    ) {}
}
