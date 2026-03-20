package com.developer.exception;

import com.developer.dto.AiValidationError;
import lombok.Getter;

import java.util.List;

/**
 * AI 校验失败异常
 */
@Getter
public class AiValidationFailedException extends AiGenerationException {

    private final List<AiValidationError> errors;

    public AiValidationFailedException(List<AiValidationError> errors) {
        super("AI_VALIDATION_FAILED", "AI 生成数据校验失败");
        this.errors = errors;
    }
}
