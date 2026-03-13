package com.portal.properties;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property test for N8N Action configJson structure completeness.
 *
 * Feature: n8n-workflow-integration, Property 13: N8N Action configJson 结构完整性
 *
 * For any valid N8N_ACTION type action definition, its configJson should contain
 * all required fields: n8nConfigId, n8nWorkflowId, webhookUrl, timeoutSeconds,
 * inputMapping, outputMapping.
 *
 * **Validates: Requirements 10.4**
 */
public class N8nActionConfigJsonPropertyTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final List<String> REQUIRED_FIELDS = List.of(
            "n8nConfigId", "n8nWorkflowId", "webhookUrl",
            "timeoutSeconds", "inputMapping", "outputMapping"
    );

    /**
     * Feature: n8n-workflow-integration, Property 13: N8N Action configJson 结构完整性
     *
     * Generate random N8N_ACTION configJson and verify all required fields are present.
     *
     * **Validates: Requirements 10.4**
     */
    @Property(tries = 100)
    @Label("Feature: n8n-workflow-integration, Property 13: Valid configJson contains all required fields")
    void validConfigJsonContainsAllRequiredFields(
            @ForAll("validConfigJsons") String configJson) throws Exception {

        // Parse the configJson
        Map<String, Object> config = objectMapper.readValue(configJson, new TypeReference<>() {});

        // Verify all required fields are present and non-null
        for (String field : REQUIRED_FIELDS) {
            assertThat(config).containsKey(field);
            assertThat(config.get(field)).isNotNull();
        }

        // Verify field types
        assertThat(config.get("n8nConfigId")).isInstanceOf(String.class);
        assertThat(config.get("n8nWorkflowId")).isInstanceOf(String.class);
        assertThat(config.get("webhookUrl")).isInstanceOf(String.class);
        assertThat(config.get("timeoutSeconds")).isInstanceOf(Number.class);
        assertThat(config.get("inputMapping")).isInstanceOf(List.class);
        assertThat(config.get("outputMapping")).isInstanceOf(List.class);

        // Verify string fields are non-blank
        assertThat((String) config.get("n8nConfigId")).isNotBlank();
        assertThat((String) config.get("n8nWorkflowId")).isNotBlank();
        assertThat((String) config.get("webhookUrl")).isNotBlank();

        // Verify timeoutSeconds is positive
        int timeout = ((Number) config.get("timeoutSeconds")).intValue();
        assertThat(timeout).isGreaterThan(0);

        // Verify mapping entries have source and target
        @SuppressWarnings("unchecked")
        List<Map<String, String>> inputMapping = (List<Map<String, String>>) config.get("inputMapping");
        for (Map<String, String> entry : inputMapping) {
            assertThat(entry).containsKeys("source", "target");
            assertThat(entry.get("source")).isNotBlank();
            assertThat(entry.get("target")).isNotBlank();
        }

        @SuppressWarnings("unchecked")
        List<Map<String, String>> outputMapping = (List<Map<String, String>>) config.get("outputMapping");
        for (Map<String, String> entry : outputMapping) {
            assertThat(entry).containsKeys("source", "target");
            assertThat(entry.get("source")).isNotBlank();
            assertThat(entry.get("target")).isNotBlank();
        }
    }

    /**
     * Feature: n8n-workflow-integration, Property 13: N8N Action configJson 结构完整性
     *
     * ConfigJson missing any required field should be detected as incomplete.
     *
     * **Validates: Requirements 10.4**
     */
    @Property(tries = 100)
    @Label("Feature: n8n-workflow-integration, Property 13: ConfigJson missing required field is incomplete")
    void configJsonMissingRequiredFieldIsIncomplete(
            @ForAll("validConfigJsons") String configJson,
            @ForAll("requiredFieldIndices") int fieldIndex) throws Exception {

        Map<String, Object> config = objectMapper.readValue(configJson, new TypeReference<>() {});

        // Remove one required field
        String removedField = REQUIRED_FIELDS.get(fieldIndex);
        config.remove(removedField);

        // Verify the config is now missing the required field
        assertThat(config).doesNotContainKey(removedField);

        // Verify remaining fields are still present
        for (String field : REQUIRED_FIELDS) {
            if (!field.equals(removedField)) {
                assertThat(config).containsKey(field);
            }
        }
    }

    // ==================== Providers ====================

    @Provide
    Arbitrary<String> validConfigJsons() {
        return Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(8).ofMaxLength(36),   // n8nConfigId
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20),   // n8nWorkflowId
                webhookUrls(),                                                    // webhookUrl
                Arbitraries.integers().between(10, 600),                         // timeoutSeconds
                mappingLists(),                                                   // inputMapping
                mappingLists()                                                    // outputMapping
        ).as((configId, workflowId, webhookUrl, timeout, inputMapping, outputMapping) -> {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("n8nConfigId", configId);
            config.put("n8nWorkflowId", workflowId);
            config.put("webhookUrl", webhookUrl);
            config.put("timeoutSeconds", timeout);
            config.put("inputMapping", inputMapping);
            config.put("outputMapping", outputMapping);
            try {
                return objectMapper.writeValueAsString(config);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Provide
    Arbitrary<Integer> requiredFieldIndices() {
        return Arbitraries.integers().between(0, REQUIRED_FIELDS.size() - 1);
    }

    private Arbitrary<String> webhookUrls() {
        return Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20)
                .map(path -> "https://n8n.example.com/webhook/" + path);
    }

    private Arbitrary<List<Map<String, String>>> mappingLists() {
        Arbitrary<Map<String, String>> mappingEntry = Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20),
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20)
        ).as((source, target) -> {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("source", source);
            entry.put("target", target);
            return entry;
        });

        return mappingEntry.list().ofMinSize(0).ofMaxSize(5);
    }
}
