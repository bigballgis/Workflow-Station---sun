package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.functionunit.CatalogSnapshotReader;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogComputedVersionIsolationTest {
    @Test void twoFunctionUnitsAndTwoReleasesUseTheirOwnRulesOnColdAndWarmReads() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var keys = mock(PortalPrimaryKeyAllocationComponent.class);
        var reader = mock(CatalogSnapshotReader.class);
        var calculator = new ComputedFieldRecalculator(jdbc, new ObjectMapper(), keys);
        ReflectionTestUtils.setField(calculator, "enabled", true);
        ReflectionTestUtils.setField(calculator, "catalogSnapshots", reader);
        snapshot(reader, "purchase-v1", "orders", "value", "total", 100);
        snapshot(reader, "purchase-v2", "orders", "value", "total", 200);
        snapshot(reader, "travel-v1", "expense_entries", "distance", "allowance", 7);
        snapshot(reader, "travel-v2", "expense_entries", "distance", "allowance", 11);
        for (int pass = 0; pass < 2; pass++) {
            verifyValue(calculator, "purchase-v1", "value", "total", 100);
            verifyValue(calculator, "purchase-v2", "value", "total", 200);
            verifyValue(calculator, "travel-v1", "distance", "allowance", 7);
            verifyValue(calculator, "travel-v2", "distance", "allowance", 11);
        }
        for (String id : List.of("purchase-v1", "purchase-v2", "travel-v1", "travel-v2")) {
            verify(reader, times(1)).contents(id, "DATA_TABLE");
        }
        verifyNoInteractions(jdbc, keys); // No global existence probe and no live DW metadata.
    }

    private void snapshot(CatalogSnapshotReader reader, String id, String table, String input, String output, int factor) {
        Map<String, Object> ast = Map.of("type", "binary", "op", "*",
                "left", Map.of("type", "field", "name", input),
                "right", Map.of("type", "number", "text", String.valueOf(factor)));
        when(reader.contents(id, "DATA_TABLE")).thenReturn(List.of(Map.of("tableId", 23, "tableName", table,
                "tableType", "MAIN", "fields", List.of(Map.of("fieldName", output, "isComputed", true,
                        "computedField", Map.of("ast", ast, "onError", "fail"))))));
        when(reader.contents(id, "FORM")).thenReturn(List.of(Map.of("tableBindings", List.of())));
    }

    private void verifyValue(ComputedFieldRecalculator calculator, String catalog, String input, String output, int expected) {
        var values = new HashMap<String, Object>(Map.of("functionUnitId", catalog, input, 1));
        calculator.recalculate("a-code-is-not-a-version", values);
        assertThat(values).as("release %s must execute its published formula", catalog).containsKey(output);
        assertThat(new BigDecimal(values.get(output).toString())).isEqualByComparingTo(BigDecimal.valueOf(expected));
    }
}
