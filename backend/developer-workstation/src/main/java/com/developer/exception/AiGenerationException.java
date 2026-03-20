package com.developer.exception;

/**
 * AI 生成相关业务异常
 */
public class AiGenerationException extends BusinessException {

    public AiGenerationException(String errorCode, String message) {
        super(errorCode, message);
    }
}
