package com.developer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * AI 生成的结构化数据 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiGeneratedData {

    private List<Map<String, Object>> tableDefinitions;

    private List<Map<String, Object>> formDefinitions;

    private List<Map<String, Object>> actionDefinitions;

    private List<Map<String, Object>> decisionDefinitions;

    private List<Map<String, Object>> tableRelations;

    private Map<String, Object> processDefinition;

    private Map<String, Object> icon;

    private String name;

    private String description;

    private Map<String, String> explanations;
}
