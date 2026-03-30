package com.developer.exception;

import java.util.Map;

/**
 * AI 生成相关业务异常
 */
public class AiGenerationException extends BusinessException {

    /** 附加数据（如降级信息），可选 */
    private final Map<String, Object> extraData;

    public AiGenerationException(String errorCode, String message) {
        super(errorCode, message);
        this.extraData = null;
    }

    public AiGenerationException(String errorCode, String message, Map<String, Object> extraData) {
        super(errorCode, message);
        this.extraData = extraData;
    }

    public Map<String, Object> getExtraData() {
        return extraData;
    }
}
