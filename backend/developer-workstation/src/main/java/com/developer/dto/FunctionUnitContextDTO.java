package com.developer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 功能单元上下文序列化 DTO（发送给 AI webhook）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunctionUnitContextDTO {

    private Long functionUnitId;

    private String name;

    private String description;

    private List<Map<String, Object>> tableDefinitions;

    private List<Map<String, Object>> formDefinitions;

    private List<Map<String, Object>> actionDefinitions;

    private List<Map<String, Object>> decisionDefinitions;

    private List<Map<String, Object>> tableRelations;

    private Map<String, Object> processDefinition;

    private Map<String, Object> icon;
}
