package com.workflow.properties;

import com.workflow.util.N8nVariableMappingUtil;
import com.workflow.util.N8nVariableMappingUtil.VariableMapping;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.Size;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature: n8n-workflow-integration, Property 6: 变量映射数据转换正确性
 *
 * 对于任意有效的变量映射配置（[{source, target}]）和源数据集合，
 * 应用映射转换后，目标数据中每个 target 字段的值应等于源数据中对应 source 字段的值。
 * 此属性同时适用于输入映射（流程变量 → N8N 参数）和输出映射（N8N 输出 → 流程变量）。
 *
 * Validates: Requirements 4.3, 5.4
 */
@Label("Feature: n8n-workflow-integration, Property 6: 变量映射数据转换正确性")
public class N8nVariableMappingProperties {

    /**
     * 属性测试：输入映射转换正确性
     * 对于任意映射配置和源数据，映射后每个 target 的值等于源数据中对应 source 的值
     */
    @Property(tries = 100)
    @Label("输入映射：target 值等于 source 对应值")
    void inputMappingPreservesValues(
            @ForAll("uniqueMappingsAndData") MappingAndData data) {

        Map<String, Object> result = N8nVariableMappingUtil.applyInputMapping(
                data.mappings, data.sourceData);

        for (VariableMapping mapping : data.mappings) {
            Object expected = data.sourceData.get(mapping.getSource());
            assertThat(result).containsKey(mapping.getTarget());
            assertThat(result.get(mapping.getTarget())).isEqualTo(expected);
        }
    }

    /**
     * 属性测试：输出映射转换正确性
     * 对于任意映射配置和 N8N 输出数据，映射后每个 target 的值等于 N8N 输出中对应 source 的值
     */
    @Property(tries = 100)
    @Label("输出映射：target 值等于 source 对应值")
    void outputMappingPreservesValues(
            @ForAll("uniqueMappingsAndData") MappingAndData data) {

        Map<String, Object> result = N8nVariableMappingUtil.applyOutputMapping(
                data.mappings, data.sourceData);

        for (VariableMapping mapping : data.mappings) {
            Object expected = data.sourceData.get(mapping.getSource());
            assertThat(result).containsKey(mapping.getTarget());
            assertThat(result.get(mapping.getTarget())).isEqualTo(expected);
        }
    }

    /**
     * 属性测试：映射结果大小等于映射配置数量
     */
    @Property(tries = 100)
    @Label("映射结果键数量等于映射配置数量")
    void mappingResultSizeMatchesMappingCount(
            @ForAll("uniqueMappingsAndData") MappingAndData data) {

        Map<String, Object> inputResult = N8nVariableMappingUtil.applyInputMapping(
                data.mappings, data.sourceData);
        Map<String, Object> outputResult = N8nVariableMappingUtil.applyOutputMapping(
                data.mappings, data.sourceData);

        // unique target count
        long uniqueTargets = data.mappings.stream()
                .map(VariableMapping::getTarget)
                .distinct()
                .count();

        assertThat(inputResult).hasSize((int) uniqueTargets);
        assertThat(outputResult).hasSize((int) uniqueTargets);
    }

    /**
     * 属性测试：空映射配置返回空结果
     */
    @Property(tries = 100)
    @Label("空映射配置返回空 Map")
    void emptyMappingsReturnEmptyResult(
            @ForAll("arbitrarySourceData") Map<String, Object> sourceData) {

        Map<String, Object> inputResult = N8nVariableMappingUtil.applyInputMapping(
                Collections.emptyList(), sourceData);
        Map<String, Object> outputResult = N8nVariableMappingUtil.applyOutputMapping(
                Collections.emptyList(), sourceData);

        assertThat(inputResult).isEmpty();
        assertThat(outputResult).isEmpty();
    }

    /**
     * 属性测试：源数据中不存在的 source 字段映射为 null
     */
    @Property(tries = 100)
    @Label("源数据中不存在的 source 字段映射值为 null")
    void missingSourceFieldsMappedAsNull(
            @ForAll @NotBlank @Size(min = 1, max = 20) String source,
            @ForAll @NotBlank @Size(min = 1, max = 20) String target) {

        VariableMapping mapping = new VariableMapping(source, target);
        // empty source data — source key does not exist
        Map<String, Object> emptySource = Collections.emptyMap();

        Map<String, Object> result = N8nVariableMappingUtil.applyInputMapping(
                List.of(mapping), emptySource);

        assertThat(result).containsKey(target);
        assertThat(result.get(target)).isNull();
    }

    // ==================== Providers ====================

    /**
     * 生成唯一映射配置和匹配的源数据
     */
    @Provide
    Arbitrary<MappingAndData> uniqueMappingsAndData() {
        Arbitrary<Integer> countArb = Arbitraries.integers().between(1, 5);

        return countArb.flatMap(count -> {
            // Generate unique source keys and unique target keys
            Arbitrary<List<String>> sourceKeysArb = Arbitraries.strings()
                    .alpha().ofMinLength(1).ofMaxLength(15)
                    .list().ofSize(count).uniqueElements();
            Arbitrary<List<String>> targetKeysArb = Arbitraries.strings()
                    .alpha().ofMinLength(1).ofMaxLength(15)
                    .list().ofSize(count).uniqueElements();
            Arbitrary<List<String>> valuesArb = Arbitraries.strings()
                    .ofMinLength(0).ofMaxLength(50)
                    .list().ofSize(count);

            return Combinators.combine(sourceKeysArb, targetKeysArb, valuesArb)
                    .as((sources, targets, values) -> {
                        List<VariableMapping> mappings = new ArrayList<>();
                        Map<String, Object> sourceData = new LinkedHashMap<>();
                        for (int i = 0; i < count; i++) {
                            mappings.add(new VariableMapping(sources.get(i), targets.get(i)));
                            sourceData.put(sources.get(i), values.get(i));
                        }
                        return new MappingAndData(mappings, sourceData);
                    });
        });
    }

    /**
     * 生成任意源数据 Map
     */
    @Provide
    Arbitrary<Map<String, Object>> arbitrarySourceData() {
        return Arbitraries.maps(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10),
                Arbitraries.strings().ofMinLength(0).ofMaxLength(30).map(s -> (Object) s)
        ).ofMinSize(0).ofMaxSize(5);
    }

    // ==================== Helper class ====================

    static class MappingAndData {
        final List<VariableMapping> mappings;
        final Map<String, Object> sourceData;

        MappingAndData(List<VariableMapping> mappings, Map<String, Object> sourceData) {
            this.mappings = mappings;
            this.sourceData = sourceData;
        }

        @Override
        public String toString() {
            return "MappingAndData{mappings=" + mappings.stream()
                    .map(m -> m.getSource() + "->" + m.getTarget())
                    .collect(Collectors.joining(", ")) +
                    ", sourceData=" + sourceData + "}";
        }
    }
}
