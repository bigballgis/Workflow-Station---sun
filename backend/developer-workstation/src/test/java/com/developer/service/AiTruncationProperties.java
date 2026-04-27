package com.developer.service;

import com.developer.dto.FunctionUnitContextDTO;
import com.developer.service.impl.AiGenerationServiceImpl;
import com.developer.repository.AiDocumentRepository;
import com.developer.repository.AiMessageRepository;
import com.developer.repository.AiSessionRepository;
import com.developer.repository.FunctionUnitRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Tag;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

/**
 * Property-based tests for AI context truncation strategy.
 *
 * <p><b>Feature: ai-function-unit-generation-refactor</b></p>
 */
@Tag("Feature: ai-function-unit-generation-refactor, Property 2: tiered truncation preserves business logic fields")
class AiTruncationProperties {

    private AiGenerationServiceImpl createService() {
        return new AiGenerationServiceImpl(
                mock(AiSessionRepository.class),
                mock(AiMessageRepository.class),
                mock(AiDocumentRepository.class),
                mock(FunctionUnitRepository.class),
                new ObjectMapper(),
                102400
        );
    }

    /**
     * Feature: ai-function-unit-generation-refactor, Property 2: tiered truncation preserves business logic fields
     *
     * <p>Verifies that tier-1 truncation preserves formulas/linkages/crossFieldRules/summaryRules
     * but truncates the rule array, and actionDefinitions configJson remains unchanged.</p>
     *
     * <p><b>Validates: Requirements 4.1, 4.4</b></p>
     */
    @Property(tries = 100)
    @Label("Property 2: tiered truncation preserves business logic fields")
    @SuppressWarnings("unchecked")
    void tieredTruncationPreservesBusinessLogicFields(
            @ForAll @IntRange(min = 1, max = 10) int ruleCount) {

        AiGenerationServiceImpl service = createService();

        // Build configJson with rule array and business logic extension fields
        List<Map<String, Object>> rules = new ArrayList<>();
        for (int i = 0; i < ruleCount; i++) {
            rules.add(Map.of("field", "field_" + i, "condition", "equals", "value", "val_" + i));
        }

        List<Map<String, Object>> formulas = List.of(
                Map.of("targetField", "total", "expression", "a+b", "dependsOn", List.of("a", "b")));
        List<Map<String, Object>> linkages = List.of(
                Map.of("sourceField", "dept", "targetField", "manager", "linkageType", "value-auto-fill"));
        List<Map<String, Object>> crossFieldRules = List.of(
                Map.of("fields", List.of("start", "end"), "operator", "less-than", "message", "Start < End"));
        List<Map<String, Object>> summaryRules = List.of(
                Map.of("sourceColumn", "amount", "targetField", "total", "aggregation", "SUM"));

        Map<String, Object> configJson = new LinkedHashMap<>();
        configJson.put("rule", new ArrayList<>(rules));
        configJson.put("formulas", formulas);
        configJson.put("linkages", linkages);
        configJson.put("crossFieldRules", crossFieldRules);
        configJson.put("summaryRules", summaryRules);

        Map<String, Object> formMap = new LinkedHashMap<>();
        formMap.put("formName", "testForm");
        formMap.put("configJson", configJson);

        // Action configJson should remain untouched
        Map<String, Object> actionConfigJson = new LinkedHashMap<>();
        actionConfigJson.put("visibilityCondition", Map.of("field", "status", "operator", "equals", "value", "active"));
        actionConfigJson.put("allowedRoles", List.of("ADMIN"));

        Map<String, Object> actionMap = new LinkedHashMap<>();
        actionMap.put("actionName", "testAction");
        actionMap.put("configJson", actionConfigJson);

        FunctionUnitContextDTO dto = FunctionUnitContextDTO.builder()
                .functionUnitId(1L)
                .name("test")
                .formDefinitions(List.of(formMap))
                .actionDefinitions(List.of(actionMap))
                .build();

        // Invoke truncateConfigJson via reflection
        ReflectionTestUtils.invokeMethod(service, "truncateConfigJson", dto);

        // Verify form configJson
        Map<String, Object> resultConfig = (Map<String, Object>) dto.getFormDefinitions().get(0).get("configJson");

        // rule array should be truncated
        List<Map<String, Object>> resultRules = (List<Map<String, Object>>) resultConfig.get("rule");
        assertThat(resultRules).hasSize(1);
        assertThat(resultRules.get(0).get("truncated")).isEqualTo(true);
        assertThat(resultRules.get(0).get("originalCount")).isEqualTo(ruleCount);

        // Business logic fields should be preserved
        assertThat(resultConfig.get("formulas")).isEqualTo(formulas);
        assertThat(resultConfig.get("linkages")).isEqualTo(linkages);
        assertThat(resultConfig.get("crossFieldRules")).isEqualTo(crossFieldRules);
        assertThat(resultConfig.get("summaryRules")).isEqualTo(summaryRules);

        // Action configJson should be completely unchanged
        Map<String, Object> resultActionConfig = (Map<String, Object>) dto.getActionDefinitions().get(0).get("configJson");
        assertThat(resultActionConfig).isEqualTo(actionConfigJson);
    }

    /**
     * Feature: ai-function-unit-generation-refactor, Property 17: Map mutability after truncation
     *
     * <p>Verifies that truncated Maps are mutable — put() does not throw
     * UnsupportedOperationException.</p>
     *
     * <p><b>Validates: Requirements 40.1, 40.2</b></p>
     */
    @Property(tries = 100)
    @Label("Property 17: Map mutability after truncation")
    @SuppressWarnings("unchecked")
    void truncatedConfigJsonMapsAreMutable(
            @ForAll @IntRange(min = 1, max = 5) int ruleCount) {

        AiGenerationServiceImpl service = createService();

        // Build configJson with rule array
        List<Map<String, Object>> rules = new ArrayList<>();
        for (int i = 0; i < ruleCount; i++) {
            rules.add(Map.of("field", "f_" + i));
        }

        Map<String, Object> configJson = new LinkedHashMap<>();
        configJson.put("rule", new ArrayList<>(rules));

        Map<String, Object> formMap = new LinkedHashMap<>();
        formMap.put("formName", "testForm");
        formMap.put("configJson", configJson);

        FunctionUnitContextDTO dto = FunctionUnitContextDTO.builder()
                .functionUnitId(1L)
                .name("test")
                .formDefinitions(List.of(formMap))
                .actionDefinitions(List.of())
                .build();

        // Invoke truncateConfigJson
        ReflectionTestUtils.invokeMethod(service, "truncateConfigJson", dto);

        // Verify tier-1 truncated configJson Map is mutable
        Map<String, Object> resultConfig = (Map<String, Object>) dto.getFormDefinitions().get(0).get("configJson");
        assertThatCode(() -> resultConfig.put("extraKey", "extraValue"))
                .as("Tier-1 truncated configJson Map should be mutable")
                .doesNotThrowAnyException();

        // Verify the truncated rule entry Map is also mutable
        List<Map<String, Object>> resultRules = (List<Map<String, Object>>) resultConfig.get("rule");
        assertThatCode(() -> resultRules.get(0).put("anotherKey", "anotherValue"))
                .as("Truncated rule entry Map should be mutable")
                .doesNotThrowAnyException();
    }

    /**
     * Feature: ai-function-unit-generation-refactor, Property 17 (tier-2): 第二级截断 Map 可变性
     *
     * <p>Verifies that tier-2 truncated Maps (entire configJson replaced) are also mutable.</p>
     *
     * <p><b>Validates: Requirements 40.1, 40.2</b></p>
     */
    @Property(tries = 50)
    @Label("Property 17: tier-2 Map mutability after truncation")
    @SuppressWarnings("unchecked")
    void tier2TruncatedConfigJsonMapsAreMutable(
            @ForAll @IntRange(min = 3, max = 8) int formCount) {

        // Use a very small maxContextSizeBytes to force tier-2 truncation
        AiGenerationServiceImpl service = new AiGenerationServiceImpl(
                mock(AiSessionRepository.class),
                mock(AiMessageRepository.class),
                mock(AiDocumentRepository.class),
                mock(FunctionUnitRepository.class),
                new ObjectMapper(),
                500 // Very small limit to force tier-2
        );

        List<Map<String, Object>> formDefs = new ArrayList<>();
        for (int i = 0; i < formCount; i++) {
            Map<String, Object> configJson = new LinkedHashMap<>();
            // Large rule array to exceed limit even after tier-1
            List<Map<String, Object>> rules = new ArrayList<>();
            for (int j = 0; j < 50; j++) {
                rules.add(Map.of("field", "f_" + j, "data", "x".repeat(100)));
            }
            configJson.put("rule", rules);
            configJson.put("formulas", List.of(Map.of("targetField", "t", "expression", "a+b")));

            Map<String, Object> formMap = new LinkedHashMap<>();
            formMap.put("formName", "form_" + i);
            formMap.put("configJson", configJson);
            formDefs.add(formMap);
        }

        FunctionUnitContextDTO dto = FunctionUnitContextDTO.builder()
                .functionUnitId(1L)
                .name("test")
                .formDefinitions(formDefs)
                .actionDefinitions(List.of())
                .build();

        ReflectionTestUtils.invokeMethod(service, "truncateConfigJson", dto);

        // After tier-2, all configJson should be {"truncated": true} and mutable
        for (Map<String, Object> form : dto.getFormDefinitions()) {
            Map<String, Object> resultConfig = (Map<String, Object>) form.get("configJson");
            assertThat(resultConfig.get("truncated")).isEqualTo(true);
            assertThatCode(() -> resultConfig.put("extraKey", "extraValue"))
                    .as("Tier-2 truncated configJson Map should be mutable")
                    .doesNotThrowAnyException();
        }
    }
}
