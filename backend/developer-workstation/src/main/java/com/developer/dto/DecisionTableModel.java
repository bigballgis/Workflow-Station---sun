package com.developer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 决策表结构化模型 DTO
 * 用于 DMN XML 与 JSON 之间的双向转换
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionTableModel {

    private String decisionKey;
    private String decisionName;
    private String hitPolicy;
    private List<InputColumn> inputs;
    private List<OutputColumn> outputs;
    private List<Rule> rules;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InputColumn {
        private String label;
        private String inputExpression;
        private String typeRef;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OutputColumn {
        private String label;
        private String name;
        private String typeRef;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Rule {
        private List<String> inputEntries;
        private List<String> outputEntries;
    }
}
