package com.workflow.util;

import com.workflow.util.N8nVariableMappingUtil.VariableMapping;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * N8nVariableMappingUtil 单元测试
 * 覆盖 JSON 解析、输入映射、输出映射、边界情况和错误处理
 */
class N8nVariableMappingUtilTest {

    // ==================== parseMappingJson ====================

    @Test
    void parseMappingJson_validJson_returnsMappings() {
        String json = "[{\"source\":\"var1\",\"target\":\"param1\"},{\"source\":\"var2\",\"target\":\"param2\"}]";
        List<VariableMapping> result = N8nVariableMappingUtil.parseMappingJson(json);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSource()).isEqualTo("var1");
        assertThat(result.get(0).getTarget()).isEqualTo("param1");
        assertThat(result.get(1).getSource()).isEqualTo("var2");
        assertThat(result.get(1).getTarget()).isEqualTo("param2");
    }

    @Test
    void parseMappingJson_nullInput_returnsEmptyList() {
        assertThat(N8nVariableMappingUtil.parseMappingJson(null)).isEmpty();
    }

    @Test
    void parseMappingJson_emptyString_returnsEmptyList() {
        assertThat(N8nVariableMappingUtil.parseMappingJson("")).isEmpty();
    }

    @Test
    void parseMappingJson_blankString_returnsEmptyList() {
        assertThat(N8nVariableMappingUtil.parseMappingJson("   ")).isEmpty();
    }

    @Test
    void parseMappingJson_emptyArray_returnsEmptyList() {
        assertThat(N8nVariableMappingUtil.parseMappingJson("[]")).isEmpty();
    }

    @Test
    void parseMappingJson_invalidJson_throwsIllegalArgument() {
        assertThatThrownBy(() -> N8nVariableMappingUtil.parseMappingJson("not-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid variable mapping JSON");
    }

    // ==================== applyInputMapping (JSON string) ====================

    @Test
    void applyInputMapping_jsonString_mapsCorrectly() {
        Map<String, Object> processVars = Map.of("orderId", "ORD-001", "amount", 99.5);
        String json = "[{\"source\":\"orderId\",\"target\":\"order_id\"},{\"source\":\"amount\",\"target\":\"total\"}]";

        Map<String, Object> result = N8nVariableMappingUtil.applyInputMapping(processVars, json);

        assertThat(result).hasSize(2);
        assertThat(result.get("order_id")).isEqualTo("ORD-001");
        assertThat(result.get("total")).isEqualTo(99.5);
    }

    @Test
    void applyInputMapping_nullJson_returnsEmptyMap() {
        Map<String, Object> processVars = Map.of("key", "value");
        assertThat(N8nVariableMappingUtil.applyInputMapping(processVars, (String) null)).isEmpty();
    }

    @Test
    void applyInputMapping_emptyJson_returnsEmptyMap() {
        Map<String, Object> processVars = Map.of("key", "value");
        assertThat(N8nVariableMappingUtil.applyInputMapping(processVars, "")).isEmpty();
    }

    @Test
    void applyInputMapping_missingSourceKey_mapsToNull() {
        Map<String, Object> processVars = Map.of("existingKey", "value");
        String json = "[{\"source\":\"missingKey\",\"target\":\"output\"}]";

        Map<String, Object> result = N8nVariableMappingUtil.applyInputMapping(processVars, json);

        assertThat(result).containsKey("output");
        assertThat(result.get("output")).isNull();
    }

    @Test
    void applyInputMapping_nullProcessVars_mapsToNull() {
        String json = "[{\"source\":\"key\",\"target\":\"output\"}]";

        Map<String, Object> result = N8nVariableMappingUtil.applyInputMapping(null, json);

        assertThat(result).containsKey("output");
        assertThat(result.get("output")).isNull();
    }

    // ==================== applyOutputMapping (JSON string) ====================

    @Test
    void applyOutputMapping_jsonString_mapsCorrectly() {
        Map<String, Object> n8nOutput = Map.of("result", "success", "data", List.of(1, 2, 3));
        String json = "[{\"source\":\"result\",\"target\":\"processResult\"},{\"source\":\"data\",\"target\":\"processData\"}]";

        Map<String, Object> result = N8nVariableMappingUtil.applyOutputMapping(n8nOutput, json);

        assertThat(result).hasSize(2);
        assertThat(result.get("processResult")).isEqualTo("success");
        assertThat(result.get("processData")).isEqualTo(List.of(1, 2, 3));
    }

    @Test
    void applyOutputMapping_nullJson_returnsEmptyMap() {
        Map<String, Object> n8nOutput = Map.of("key", "value");
        assertThat(N8nVariableMappingUtil.applyOutputMapping(n8nOutput, (String) null)).isEmpty();
    }

    @Test
    void applyOutputMapping_nullN8nOutput_mapsToNull() {
        String json = "[{\"source\":\"key\",\"target\":\"output\"}]";

        Map<String, Object> result = N8nVariableMappingUtil.applyOutputMapping(null, json);

        assertThat(result).containsKey("output");
        assertThat(result.get("output")).isNull();
    }

    // ==================== applyInputMapping (List<VariableMapping>) ====================

    @Test
    void applyInputMapping_list_mapsCorrectly() {
        List<VariableMapping> mappings = List.of(
                new VariableMapping("a", "x"),
                new VariableMapping("b", "y")
        );
        Map<String, Object> source = Map.of("a", 1, "b", "hello");

        Map<String, Object> result = N8nVariableMappingUtil.applyInputMapping(mappings, source);

        assertThat(result).hasSize(2);
        assertThat(result.get("x")).isEqualTo(1);
        assertThat(result.get("y")).isEqualTo("hello");
    }

    @Test
    void applyInputMapping_nullMappings_returnsEmptyMap() {
        assertThat(N8nVariableMappingUtil.applyInputMapping((List<VariableMapping>) null, Map.of("a", 1))).isEmpty();
    }

    @Test
    void applyInputMapping_mappingWithNullSourceOrTarget_skipped() {
        List<VariableMapping> mappings = List.of(
                new VariableMapping(null, "target1"),
                new VariableMapping("source2", null),
                new VariableMapping("source3", "target3")
        );
        Map<String, Object> source = Map.of("source3", "val3");

        Map<String, Object> result = N8nVariableMappingUtil.applyInputMapping(mappings, source);

        assertThat(result).hasSize(1);
        assertThat(result.get("target3")).isEqualTo("val3");
    }

    // ==================== applyOutputMapping (List<VariableMapping>) ====================

    @Test
    void applyOutputMapping_list_mapsCorrectly() {
        List<VariableMapping> mappings = List.of(new VariableMapping("out1", "var1"));
        Map<String, Object> n8nOutput = Map.of("out1", 42);

        Map<String, Object> result = N8nVariableMappingUtil.applyOutputMapping(mappings, n8nOutput);

        assertThat(result).hasSize(1);
        assertThat(result.get("var1")).isEqualTo(42);
    }

    @Test
    void applyOutputMapping_nullMappings_returnsEmptyMap() {
        assertThat(N8nVariableMappingUtil.applyOutputMapping((List<VariableMapping>) null, Map.of("a", 1))).isEmpty();
    }

    // ==================== resolveNestedValue (via applyOutputMapping) ====================
    // Task 10.4: Unit tests for resolveNestedValue

    @Test
    void applyOutputMapping_nestedMapTraversal_resolvesCorrectly() {
        // {"a": {"b": {"c": "value"}}} with source "a.b.c" → "value"
        Map<String, Object> nested = new HashMap<>();
        nested.put("a", Map.of("b", Map.of("c", "value")));

        List<VariableMapping> mappings = List.of(new VariableMapping("a.b.c", "result"));
        Map<String, Object> result = N8nVariableMappingUtil.applyOutputMapping(mappings, nested);

        assertThat(result.get("result")).isEqualTo("value");
    }

    @Test
    void applyOutputMapping_missingIntermediateKey_returnsNull() {
        // {"a": {"b": 1}} with source "a.x.c" → null
        Map<String, Object> nested = new HashMap<>();
        nested.put("a", Map.of("b", 1));

        List<VariableMapping> mappings = List.of(new VariableMapping("a.x.c", "result"));
        Map<String, Object> result = N8nVariableMappingUtil.applyOutputMapping(mappings, nested);

        assertThat(result).containsKey("result");
        assertThat(result.get("result")).isNull();
    }

    @Test
    void applyOutputMapping_nonMapIntermediateValue_returnsNull() {
        // {"a": "string"} with source "a.b" → null
        Map<String, Object> data = new HashMap<>();
        data.put("a", "string");

        List<VariableMapping> mappings = List.of(new VariableMapping("a.b", "result"));
        Map<String, Object> result = N8nVariableMappingUtil.applyOutputMapping(mappings, data);

        assertThat(result).containsKey("result");
        assertThat(result.get("result")).isNull();
    }

    @Test
    void applyOutputMapping_flatKeyLookup_backwardCompatible() {
        // {"key": "value"} with source "key" → "value" (no dots, backward compatible)
        Map<String, Object> data = Map.of("key", "value");

        List<VariableMapping> mappings = List.of(new VariableMapping("key", "result"));
        Map<String, Object> result = N8nVariableMappingUtil.applyOutputMapping(mappings, data);

        assertThat(result.get("result")).isEqualTo("value");
    }

    @Test
    void applyOutputMapping_deeplyNestedPath_resolvesCorrectly() {
        Map<String, Object> data = new HashMap<>();
        data.put("level1", Map.of("level2", Map.of("level3", Map.of("level4", 42))));

        List<VariableMapping> mappings = List.of(new VariableMapping("level1.level2.level3.level4", "deep"));
        Map<String, Object> result = N8nVariableMappingUtil.applyOutputMapping(mappings, data);

        assertThat(result.get("deep")).isEqualTo(42);
    }

    @Test
    void applyOutputMapping_mixedFlatAndNestedSources_resolvesAll() {
        Map<String, Object> data = new HashMap<>();
        data.put("flat", "flatValue");
        data.put("nested", Map.of("child", "nestedValue"));

        List<VariableMapping> mappings = List.of(
                new VariableMapping("flat", "out1"),
                new VariableMapping("nested.child", "out2")
        );
        Map<String, Object> result = N8nVariableMappingUtil.applyOutputMapping(mappings, data);

        assertThat(result.get("out1")).isEqualTo("flatValue");
        assertThat(result.get("out2")).isEqualTo("nestedValue");
    }

    // ==================== Property 9: Backend dot notation backward compatibility ====================
    // Feature: n8n-output-autofill-generalization, Property 9: Backend dot notation backward compatibility
    // Validates: Requirements 13.4

    @Property(tries = 100)
    void backwardCompatibility_flatMapWithNoDotSources_sameAsDirectLookup(
            @ForAll("flatMapAndMappings") FlatMapAndMappings input) {
        // For any flat Map and VariableMapping with source containing no dots,
        // applyOutputMapping should produce the same result as direct key lookup
        Map<String, Object> result = N8nVariableMappingUtil.applyOutputMapping(input.mappings, input.sourceData);

        // Verify each mapping produces the same result as direct get
        for (VariableMapping mapping : input.mappings) {
            if (mapping.getSource() != null && mapping.getTarget() != null) {
                Object expected = input.sourceData.get(mapping.getSource());
                assertThat(result.get(mapping.getTarget())).isEqualTo(expected);
            }
        }
    }

    @Provide
    Arbitrary<FlatMapAndMappings> flatMapAndMappings() {
        // Generate keys that don't contain dots (flat keys)
        Arbitrary<String> flatKey = Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(10)
                .filter(s -> !s.contains("."));

        Arbitrary<String> value = Arbitraries.strings()
                .alpha()
                .ofMinLength(0)
                .ofMaxLength(20);

        // Generate a flat map with 1-5 entries
        Arbitrary<Map<String, Object>> flatMap = Arbitraries.maps(flatKey, value.map(v -> (Object) v))
                .ofMinSize(1)
                .ofMaxSize(5);

        return flatMap.flatMap(map -> {
            List<String> keys = new ArrayList<>(map.keySet());
            // Generate 1 to keys.size() mappings using existing keys as sources
            return Arbitraries.integers().between(1, Math.max(1, keys.size())).flatMap(count -> {
                int actualCount = Math.min(count, keys.size());
                List<VariableMapping> mappings = new ArrayList<>();
                for (int i = 0; i < actualCount; i++) {
                    mappings.add(new VariableMapping(keys.get(i), "target_" + i));
                }
                return Arbitraries.just(new FlatMapAndMappings(map, mappings));
            });
        });
    }

    /** Helper record for Property 9 */
    static class FlatMapAndMappings {
        final Map<String, Object> sourceData;
        final List<VariableMapping> mappings;

        FlatMapAndMappings(Map<String, Object> sourceData, List<VariableMapping> mappings) {
            this.sourceData = sourceData;
            this.mappings = mappings;
        }
    }
}
