package com.developer.dto;

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

    private String sessionId;

    private AiGeneratedData generatedData;
}
