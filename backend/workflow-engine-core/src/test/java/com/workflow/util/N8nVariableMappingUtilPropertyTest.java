package com.workflow.util;

import com.workflow.util.N8nVariableMappingUtil.VariableMapping;
import net.jqwik.api.*;
import net.jqwik.api.Tuple.Tuple2;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-Based Test for N8nVariableMappingUtil
 *
 * Feature: n8n-workflow-integration, Property 6: 变量映射数据转换正确性
 *
 * Validates: Requirements 4.3, 5.4
 *
 * For any valid variable mapping config ([{source, target}]) and source data set,
 * after applying the mapping transformation, each target field value in the destination
 * data should equal the corresponding source field value in the source data.
 * This property applies to both input mapping (process variables → N8N params)
 * and output mapping (N8N output → process variables).
 */
class N8nVariableMappingUtilPropertyTest {

    // ==================== Property 6: Input Mapping Correctness ====================

    /**
     * Feature: n8n-workflow-integration, Property 6: 变量映射数据转换正确性
     *
     * For any valid variable mapping config and source data, applying input mapping
     * should produce a result where each target key holds the value from the
     * corresponding source key in the source data.
     *
     * Validates: Requirements 4.3, 5.4
     */
    @Property(tries = 100)
    @Label("Property 6: Input mapping correctly transfers source values to target keys")
    void inputMappingTransfersValuesCorrectly(
            @ForAll("validMappingsWithData") Tuple2<List<VariableMapping>, Map<String, Object>> input) {

        List<VariableMapping> mappings = input.get1();
        Map<String, Object> sourceData = input.get2();

        // When: applying input mapping
        Map<String, Object> result = N8nVariableMappingUtil.applyInputMapping(mappings, sourceData);

        // Then: each target field should have the value from the corresponding source field
        for (VariableMapping mapping : mappings) {
            assertThat(result).containsKey(mapping.getTarget());
            assertThat(result.get(mapping.getTarget()))
                    .as("Target '%s' should equal source '%s' value", mapping.getTarget(), mapping.getSource())
                    .isEqualTo(sourceData.get(mapping.getSource()));
        }
    }

    // ==================== Property 6: Output Mapping Correctness ====================

    /**
     * Feature: n8n-workflow-integration, Property 6: 变量映射数据转换正确性
     *
     * For any valid variable mapping config and N8N output data, applying output mapping
     * should produce a result where each target key holds the value from the
     * corresponding source key in the N8N output.
     *
     * Validates: Requirements 4.3, 5.4
     */
    @Property(tries = 100)
    @Label("Property 6: Output mapping correctly transfers source values to target keys")
    void outputMappingTransfersValuesCorrectly(
            @ForAll("validMappingsWithData") Tuple2<List<VariableMapping>, Map<String, Object>> input) {

        List<VariableMapping> mappings = input.get1();
        Map<String, Object> sourceData = input.get2();

        // When: applying output mapping
        Map<String, Object> result = N8nVariableMappingUtil.applyOutputMapping(mappings, sourceData);

        // Then: each target field should have the value from the corresponding source field
        for (VariableMapping mapping : mappings) {
            assertThat(result).containsKey(mapping.getTarget());
            assertThat(result.get(mapping.getTarget()))
                    .as("Target '%s' should equal source '%s' value", mapping.getTarget(), mapping.getSource())
                    .isEqualTo(sourceData.get(mapping.getSource()));
        }
    }

    // ==================== Property 6: Result size matches mapping count ====================

    /**
     * Feature: n8n-workflow-integration, Property 6: 变量映射数据转换正确性
     *
     * The number of entries in the result map should equal the number of
     * unique target keys in the mappings.
     *
     * Validates: Requirements 4.3, 5.4
     */
    @Property(tries = 100)
    @Label("Property 6: Result map size equals number of unique target keys in mappings")
    void resultSizeMatchesUniqueMappingTargets(
            @ForAll("validMappingsWithData") Tuple2<List<VariableMapping>, Map<String, Object>> input) {

        List<VariableMapping> mappings = input.get1();
        Map<String, Object> sourceData = input.get2();

        Map<String, Object> result = N8nVariableMappingUtil.applyInputMapping(mappings, sourceData);

        long uniqueTargets = mappings.stream()
                .map(VariableMapping::getTarget)
                .distinct()
                .count();

        assertThat(result).hasSize((int) uniqueTargets);
    }

    // ==================== Property 6: Various data types ====================

    /**
     * Feature: n8n-workflow-integration, Property 6: 变量映射数据转换正确性
     *
     * Mapping should work correctly with various data types including
     * String, Integer, Double, Boolean, List, and Map.
     *
     * Validates: Requirements 4.3, 5.4
     */
    @Property(tries = 100)
    @Label("Property 6: Mapping preserves various data types correctly")
    void mappingPreservesVariousDataTypes(
            @ForAll("mappingsWithMixedTypes") Tuple2<List<VariableMapping>, Map<String, Object>> input) {

        List<VariableMapping> mappings = input.get1();
        Map<String, Object> sourceData = input.get2();

        Map<String, Object> inputResult = N8nVariableMappingUtil.applyInputMapping(mappings, sourceData);
        Map<String, Object> outputResult = N8nVariableMappingUtil.applyOutputMapping(mappings, sourceData);

        for (VariableMapping mapping : mappings) {
            Object expectedValue = sourceData.get(mapping.getSource());

            assertThat(inputResult.get(mapping.getTarget()))
                    .as("Input mapping: target '%s' type preservation", mapping.getTarget())
                    .isEqualTo(expectedValue);

            assertThat(outputResult.get(mapping.getTarget()))
                    .as("Output mapping: target '%s' type preservation", mapping.getTarget())
                    .isEqualTo(expectedValue);
        }
    }

    // ==================== Property 6: Empty mappings ====================

    /**
     * Feature: n8n-workflow-integration, Property 6: 变量映射数据转换正确性
     *
     * Empty mapping list should always produce an empty result map.
     *
     * Validates: Requirements 4.3, 5.4
     */
    @Property(tries = 100)
    @Label("Property 6: Empty mappings produce empty result")
    void emptyMappingsProduceEmptyResult(
            @ForAll("randomSourceData") Map<String, Object> sourceData) {

        List<VariableMapping> emptyMappings = Collections.emptyList();

        Map<String, Object> inputResult = N8nVariableMappingUtil.applyInputMapping(emptyMappings, sourceData);
        Map<String, Object> outputResult = N8nVariableMappingUtil.applyOutputMapping(emptyMappings, sourceData);

        assertThat(inputResult).isEmpty();
        assertThat(outputResult).isEmpty();
    }

    // ==================== Property 6: Missing source keys map to null ====================

    /**
     * Feature: n8n-workflow-integration, Property 6: 变量映射数据转换正确性
     *
     * When source keys in mappings don't exist in source data, the target
     * should be mapped to null.
     *
     * Validates: Requirements 4.3, 5.4
     */
    @Property(tries = 100)
    @Label("Property 6: Missing source keys in data map to null in result")
    void missingSourceKeysMappedToNull(
            @ForAll("mappingsWithMissingSourceKeys") Tuple2<List<VariableMapping>, Map<String, Object>> input) {

        List<VariableMapping> mappings = input.get1();
        Map<String, Object> sourceData = input.get2();

        Map<String, Object> result = N8nVariableMappingUtil.applyInputMapping(mappings, sourceData);

        for (VariableMapping mapping : mappings) {
            assertThat(result).containsKey(mapping.getTarget());
            if (!sourceData.containsKey(mapping.getSource())) {
                assertThat(result.get(mapping.getTarget()))
                        .as("Missing source key '%s' should map to null", mapping.getSource())
                        .isNull();
            }
        }
    }

    // ==================== Property 6: JSON string mapping consistency ====================

    /**
     * Feature: n8n-workflow-integration, Property 6: 变量映射数据转换正确性
     *
     * JSON string-based mapping methods should produce the same results
     * as list-based mapping methods.
     *
     * Validates: Requirements 4.3, 5.4
     */
    @Property(tries = 100)
    @Label("Property 6: JSON string mapping produces same result as list-based mapping")
    void jsonStringMappingConsistentWithListMapping(
            @ForAll("validMappingsWithData") Tuple2<List<VariableMapping>, Map<String, Object>> input) {

        List<VariableMapping> mappings = input.get1();
        Map<String, Object> sourceData = input.get2();

        String json = buildMappingJson(mappings);

        Map<String, Object> listResult = N8nVariableMappingUtil.applyInputMapping(mappings, sourceData);
        Map<String, Object> jsonResult = N8nVariableMappingUtil.applyInputMapping(sourceData, json);
        assertThat(jsonResult).isEqualTo(listResult);

        Map<String, Object> listOutputResult = N8nVariableMappingUtil.applyOutputMapping(mappings, sourceData);
        Map<String, Object> jsonOutputResult = N8nVariableMappingUtil.applyOutputMapping(sourceData, json);
        assertThat(jsonOutputResult).isEqualTo(listOutputResult);
    }

    // ==================== Providers ====================

    @Provide
    Arbitrary<Tuple2<List<VariableMapping>, Map<String, Object>>> validMappingsWithData() {
        Arbitrary<Integer> sizes = Arbitraries.integers().between(1, 8);
        return sizes.flatMap(size -> {
            // Generate unique source and target key pairs
            Arbitrary<List<String>> sourceKeys = generateUniqueKeys("src", size);
            Arbitrary<List<String>> targetKeys = generateUniqueKeys("tgt", size);
            Arbitrary<List<Object>> values = generateValueList(size);

            return Combinators.combine(sourceKeys, targetKeys, values).as((sources, targets, vals) -> {
                List<VariableMapping> mappings = new ArrayList<>();
                Map<String, Object> sourceData = new LinkedHashMap<>();
                for (int i = 0; i < size; i++) {
                    mappings.add(new VariableMapping(sources.get(i), targets.get(i)));
                    sourceData.put(sources.get(i), vals.get(i));
                }
                return Tuple.of(mappings, sourceData);
            });
        });
    }

    @Provide
    Arbitrary<Tuple2<List<VariableMapping>, Map<String, Object>>> mappingsWithMixedTypes() {
        return Arbitraries.integers().between(1, 6).flatMap(size -> {
            Arbitrary<List<String>> sourceKeys = generateUniqueKeys("msrc", size);
            Arbitrary<List<String>> targetKeys = generateUniqueKeys("mtgt", size);
            Arbitrary<List<Object>> values = generateMixedTypeValueList(size);

            return Combinators.combine(sourceKeys, targetKeys, values).as((sources, targets, vals) -> {
                List<VariableMapping> mappings = new ArrayList<>();
                Map<String, Object> sourceData = new LinkedHashMap<>();
                for (int i = 0; i < size; i++) {
                    mappings.add(new VariableMapping(sources.get(i), targets.get(i)));
                    sourceData.put(sources.get(i), vals.get(i));
                }
                return Tuple.of(mappings, sourceData);
            });
        });
    }

    @Provide
    Arbitrary<Map<String, Object>> randomSourceData() {
        return Arbitraries.integers().between(0, 5).flatMap(size -> {
            if (size == 0) {
                return Arbitraries.just(Collections.emptyMap());
            }
            Arbitrary<List<String>> keys = generateUniqueKeys("k", size);
            Arbitrary<List<Object>> values = generateValueList(size);
            return Combinators.combine(keys, values).as((ks, vs) -> {
                Map<String, Object> data = new LinkedHashMap<>();
                for (int i = 0; i < size; i++) {
                    data.put(ks.get(i), vs.get(i));
                }
                return data;
            });
        });
    }

    @Provide
    Arbitrary<Tuple2<List<VariableMapping>, Map<String, Object>>> mappingsWithMissingSourceKeys() {
        return Arbitraries.integers().between(2, 6).flatMap(size -> {
            Arbitrary<List<String>> sourceKeys = generateUniqueKeys("ms", size);
            Arbitrary<List<String>> targetKeys = generateUniqueKeys("mt", size);
            Arbitrary<List<Object>> values = generateValueList(size);
            // Randomly decide which source keys to include in source data
            Arbitrary<List<Boolean>> includes = Arbitraries.of(true, false)
                    .list().ofSize(size);

            return Combinators.combine(sourceKeys, targetKeys, values, includes)
                    .as((sources, targets, vals, incl) -> {
                        List<VariableMapping> mappings = new ArrayList<>();
                        Map<String, Object> sourceData = new LinkedHashMap<>();
                        boolean hasAtLeastOneMissing = false;
                        for (int i = 0; i < size; i++) {
                            mappings.add(new VariableMapping(sources.get(i), targets.get(i)));
                            if (incl.get(i)) {
                                sourceData.put(sources.get(i), vals.get(i));
                            } else {
                                hasAtLeastOneMissing = true;
                            }
                        }
                        // Ensure at least one key is missing
                        if (!hasAtLeastOneMissing && !mappings.isEmpty()) {
                            sourceData.remove(mappings.get(0).getSource());
                        }
                        return Tuple.of(mappings, sourceData);
                    });
        });
    }

    // ==================== Helper Arbitraries ====================

    private Arbitrary<List<String>> generateUniqueKeys(String prefix, int count) {
        return Arbitraries.integers().between(0, 99999)
                .list().ofSize(count).uniqueElements()
                .map(nums -> nums.stream()
                        .map(n -> prefix + "_" + n)
                        .collect(Collectors.toList()));
    }

    private Arbitrary<Object> arbitraryValue() {
        return Arbitraries.oneOf(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20).map(s -> (Object) s),
                Arbitraries.integers().between(-10000, 10000).map(i -> (Object) i),
                Arbitraries.doubles().between(-1000.0, 1000.0).map(d -> (Object) d),
                Arbitraries.of(true, false).map(b -> (Object) b)
        );
    }

    private Arbitrary<List<Object>> generateValueList(int size) {
        return arbitraryValue().list().ofSize(size);
    }

    private Arbitrary<List<Object>> generateMixedTypeValueList(int size) {
        // Ensure we get a mix of types: cycle through String, Integer, Double, Boolean, List, Map
        List<Arbitrary<Object>> typeArbitraries = List.of(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10).map(s -> (Object) s),
                Arbitraries.integers().between(0, 9999).map(i -> (Object) i),
                Arbitraries.doubles().between(0.0, 100.0).map(d -> (Object) d),
                Arbitraries.of(true, false).map(b -> (Object) b),
                Arbitraries.integers().between(1, 5)
                        .list().ofMinSize(1).ofMaxSize(3)
                        .map(l -> (Object) l),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(5)
                        .map(s -> (Object) Map.of("key", s))
        );

        List<Arbitrary<Object>> selected = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            selected.add(typeArbitraries.get(i % typeArbitraries.size()));
        }

        return Combinators.combine(selected).as(objects -> new ArrayList<>(objects));
    }

    // ==================== Helpers ====================

    private String buildMappingJson(List<VariableMapping> mappings) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < mappings.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"source\":\"")
                    .append(mappings.get(i).getSource())
                    .append("\",\"target\":\"")
                    .append(mappings.get(i).getTarget())
                    .append("\"}");
        }
        sb.append("]");
        return sb.toString();
    }
}
