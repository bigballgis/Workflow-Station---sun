package com.workflow.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * N8N variable mapping utility class.
 * Handles mapping conversion between workflow process variables and N8N workflow
 * input/output parameters.
 * Mapping configuration is stored in BPMN extension attributes as a JSON string
 * in the format [{source, target}].
 */
public final class N8nVariableMappingUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private N8nVariableMappingUtil() {
        // utility class
    }

    /**
     * Variable mapping configuration, defining the mapping relationship from
     * source (source field name) to target (target field name).
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
     * Parse the JSON-formatted mapping configuration string into a list of
     * VariableMapping objects.
     *
     * @param mappingJson JSON string in the format [{"source":"xxx","target":"yyy"}, ...]
     * @return parsed mapping configuration list; returns empty list if input is null
     *         or empty string
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
     * Apply input mapping (JSON string version): extract data from Flowable process
     * variables and build N8N request parameters based on the mapping configuration.
     *
     * @param processVariables process variables (source data)
     * @param inputMappingJson JSON-formatted input mapping configuration string
     * @return mapped N8N request parameters
     */
    public static Map<String, Object> applyInputMapping(Map<String, Object> processVariables,
                                                         String inputMappingJson) {
        List<VariableMapping> mappings = parseMappingJson(inputMappingJson);
        return applyInputMapping(mappings, processVariables);
    }

    /**
     * Apply output mapping (JSON string version): build process variables from
     * N8N return data based on the mapping configuration.
     *
     * @param n8nOutputData    output data returned by the N8N workflow
     * @param outputMappingJson JSON-formatted output mapping configuration string
     * @return mapped process variable data
     */
    public static Map<String, Object> applyOutputMapping(Map<String, Object> n8nOutputData,
                                                          String outputMappingJson) {
        List<VariableMapping> mappings = parseMappingJson(outputMappingJson);
        return applyOutputMapping(mappings, n8nOutputData);
    }

    /**
     * Apply input mapping: build target data (N8N request parameters) from source
     * data (process variables) based on the mapping configuration.
     * Each mapping entry writes the value of the source field from sourceVariables
     * into the target field of the result Map.
     *
     * @param mappings        mapping configuration list
     * @param sourceVariables source data (process variables)
     * @return mapped target data
     */
    public static Map<String, Object> applyInputMapping(List<VariableMapping> mappings,
                                                         Map<String, Object> sourceVariables) {
        return applyMapping(mappings, sourceVariables);
    }

    /**
     * Apply output mapping: build target data (process variables) from N8N
     * return data based on the mapping configuration.
     * Each mapping entry writes the value of the source field from n8nOutput
     * into the target field of the result Map.
     *
     * @param mappings  mapping configuration list
     * @param n8nOutput output data returned by the N8N workflow
     * @return mapped process variable data
     */
    public static Map<String, Object> applyOutputMapping(List<VariableMapping> mappings,
                                                          Map<String, Object> n8nOutput) {
        return applyMapping(mappings, n8nOutput);
    }

    /**
     * Generic mapping logic: build target data from source data based on the
     * mapping configuration.
     * Supports dot notation nested path resolution (e.g., "summary.totalAmount").
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
                Object value = resolveNestedValue(sourceData, mapping.getSource());
                result.put(mapping.getTarget(), value);
            }
        }
        return result;
    }

    /**
     * Resolve nested values using dot notation.
     * Example: resolveNestedValue({"a": {"b": 1}}, "a.b") → 1
     * For paths without dots, directly uses data.get(path) (backward compatible).
     *
     * @param data source data Map
     * @param path dot notation path
     * @return resolved value, returns null if path is invalid
     */
    @SuppressWarnings("unchecked")
    private static Object resolveNestedValue(Map<String, Object> data, String path) {
        if (!path.contains(".")) {
            return data.get(path);
        }
        String[] parts = path.split("\\.");
        Object current = data;
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return null;
            }
        }
        return current;
    }
}
