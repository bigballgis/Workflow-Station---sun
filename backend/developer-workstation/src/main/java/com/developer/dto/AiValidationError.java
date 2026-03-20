package com.developer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 校验错误 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiValidationError {

    private String errorType;

    private String fieldPath;

    private String description;
}
