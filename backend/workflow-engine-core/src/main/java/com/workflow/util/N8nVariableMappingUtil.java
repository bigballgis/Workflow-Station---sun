package com.workflow.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * N8N 变量映射工具类
 * 负责在工作流流程变量与 N8N 工作流输入/输出参数之间进行映射转换。
 * 映射配置以 JSON 字符串格式存储在 BPMN 扩展属性中，格式为 [{source, target}]。
 */
public final class N8nVariableMappingUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private N8nVariableMappingUtil() {
        // utility class
    }

    /**
     * 变量映射配置，定义 source（源字段名）到 target（目标字段名）的映射关系
     */
    public static class VariableMapping {
        private String source;
        private String target;

        public VariableMapping() {}

        public VariableMapping(String source, String target) {
            this.source = source;
            this.target = target;
        }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }

        public String getTarget() { return target; }
        public void setTarget(String target) { this.target = target; }
    }

    /**
     * 解析 JSON 格式的映射配置字符串为 VariableMapping 列表。
     *
     * @param mappingJson JSON 字符串，格式为 [{"source":"xxx","target":"yyy"}, ...]
     * @return 解析后的映射配置列表；若输入为 null 或空字符串则返回空列表
     */
    public static List<VariableMapping> parseMappingJson(String mappingJson) {
        if (mappingJson == null || mappingJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return OBJECT_MAPPER.readValue(mappingJson, new TypeReference<List<VariableMapping>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid variable mapping JSON: " + e.getMessage(), e);
        }
    }

    /**
     * 应用输入映射（JSON 字符串版本）：从 Flowable 流程变量提取数据，按映射配置构建 N8N 请求参数。
     *
     * @param processVariables 流程变量（源数据）
     * @param inputMappingJson JSON 格式的输入映射配置字符串
     * @return 映射后的 N8N 请求参数
     */
    public static Map<String, Object> applyInputMapping(Map<String, Object> processVariables,
                                                         String inputMappingJson) {
        List<VariableMapping> mappings = parseMappingJson(inputMappingJson);
        return applyInputMapping(mappings, processVariables);
    }

    /**
     * 应用输出映射（JSON 字符串版本）：从 N8N 返回数据按映射配置构建流程变量。
     *
     * @param n8nOutputData    N8N 工作流返回的输出数据
     * @param outputMappingJson JSON 格式的输出映射配置字符串
     * @return 映射后的流程变量数据
     */
    public static Map<String, Object> applyOutputMapping(Map<String, Object> n8nOutputData,
                                                          String outputMappingJson) {
        List<VariableMapping> mappings = parseMappingJson(outputMappingJson);
        return applyOutputMapping(mappings, n8nOutputData);
    }

    /**
     * 应用输入映射：从源数据（流程变量）按映射配置构建目标数据（N8N 请求参数）。
     * 每个映射项将 sourceVariables 中 source 字段的值写入结果 Map 的 target 字段。
     *
     * @param mappings        映射配置列表
     * @param sourceVariables 源数据（流程变量）
     * @return 映射后的目标数据
     */
    public static Map<String, Object> applyInputMapping(List<VariableMapping> mappings,
                                                         Map<String, Object> sourceVariables) {
        return applyMapping(mappings, sourceVariables);
    }

    /**
     * 应用输出映射：从 N8N 返回数据按映射配置构建目标数据（流程变量）。
     * 每个映射项将 n8nOutput 中 source 字段的值写入结果 Map 的 target 字段。
     *
     * @param mappings  映射配置列表
     * @param n8nOutput N8N 工作流返回的输出数据
     * @return 映射后的流程变量数据
     */
    public static Map<String, Object> applyOutputMapping(List<VariableMapping> mappings,
                                                          Map<String, Object> n8nOutput) {
        return applyMapping(mappings, n8nOutput);
    }

    /**
     * 通用映射逻辑：从源数据按映射配置构建目标数据。
     */
    private static Map<String, Object> applyMapping(List<VariableMapping> mappings,
                                                     Map<String, Object> sourceData) {
        if (mappings == null || mappings.isEmpty()) {
            return Collections.emptyMap();
        }
        if (sourceData == null) {
            sourceData = Collections.emptyMap();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (VariableMapping mapping : mappings) {
            if (mapping.getSource() != null && mapping.getTarget() != null) {
                Object value = sourceData.get(mapping.getSource());
                result.put(mapping.getTarget(), value);
            }
        }
        return result;
    }
}
