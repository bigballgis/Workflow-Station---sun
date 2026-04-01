package com.developer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 决策定义请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionDefinitionRequest {

    @NotBlank(message = "{validation.decision_key_required}")
    @Size(max = 100, message = "{validation.decision_key_max_length}")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_-]*$", message = "{validation.decision_key_pattern}")
    private String decisionKey;

    @Size(max = 200, message = "{validation.decision_name_max_length}")
    private String decisionName;

    /**
     * DMN XML；可空（草稿）。若 BPMN 或 DECISION_TABLE 动作引用该 decisionKey，则发布前须补全有效 DMN。
     */
    private String dmnXml;

    @Size(max = 20)
    private String hitPolicy;

    private String description;
}
