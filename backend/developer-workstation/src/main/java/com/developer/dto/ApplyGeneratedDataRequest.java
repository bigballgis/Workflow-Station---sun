package com.developer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 应用生成数据请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplyGeneratedDataRequest {

    @NotBlank
    @Size(max = 100)
    private String sessionId;

    private AiGeneratedData generatedData;

    /**
     * 增量重新生成范围（可选，默认 null 等同于 ALL）
     * 可选值：ALL / TABLES / FORMS / ACTIONS / DECISIONS / PROCESS / TABLE_RELATIONS
     */
    private String regenerateScope;
}
