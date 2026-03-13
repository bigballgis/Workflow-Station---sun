package com.workflow.util;

import com.workflow.util.N8nVariableMappingUtil.VariableMapping;
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
}
