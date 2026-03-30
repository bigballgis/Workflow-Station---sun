package com.developer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 校验结果 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiValidationResult {

    @Builder.Default
    private boolean valid = true;

    @Builder.Default
    private List<AiValidationError> errors = new ArrayList<>();

    @Builder.Default
    private List<AiValidationError> warnings = new ArrayList<>();

    public void addError(String errorType, String fieldPath, String description) {
        this.valid = false;
        this.errors.add(new AiValidationError(errorType, fieldPath, description));
    }

    public void addWarning(String errorType, String fieldPath, String description) {
        this.warnings.add(new AiValidationError(errorType, fieldPath, description));
    }
}
