package com.developer.service;

import com.developer.dto.AiGeneratedData;

/**
 * AI 数据写入服务
 * 负责将 AI 生成的结构化数据写入数据库，支持全量替换写入（MODIFY 模式）和直接写入（NEW 模式）
 */
public interface AiWriteService {

    /**
     * 将 AI 生成的数据应用到指定功能单元
     * <p>
     * MODIFY 模式：在单事务中先删除 FunctionUnit 下所有现有组件数据，再写入新数据
     * NEW 模式：直接写入 AI 生成的组件数据
     *
     * @param functionUnitId  功能单元 ID
     * @param generatedData   AI 生成的结构化数据
     * @param regenerateScope 增量重新生成范围（null 或 "ALL" 表示全量替换）
     */
    void applyGeneratedData(Long functionUnitId, AiGeneratedData generatedData, String regenerateScope);
}
