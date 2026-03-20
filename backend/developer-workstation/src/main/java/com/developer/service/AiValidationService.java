package com.developer.service;

import com.developer.dto.AiGeneratedData;
import com.developer.dto.AiValidationResult;

/**
 * AI 生成数据校验服务
 */
public interface AiValidationService {

    /**
     * 校验 AI 生成的数据
     *
     * @param generatedData AI 生成的结构化数据
     * @return 校验结果，包含所有校验错误
     */
    AiValidationResult validate(AiGeneratedData generatedData);
}
