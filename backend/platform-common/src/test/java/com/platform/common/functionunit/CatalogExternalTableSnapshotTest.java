package com.platform.common.functionunit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CatalogExternalTableSnapshotTest {
    @Test void historicalBindingsUseCapturedExternalTableWithoutAnEnabledParent() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class), eq("child-release"), eq("DATA_TABLE")))
                .thenReturn(List.of("""
                    {"tableId":2,"tableName":"orders","tableType":"MAIN","fields":[
                      {"fieldName":"customer_ref","isForeignKey":true,"refFunctionUnitCode":"customers",
                       "refTableName":"customers","refPrimaryKeyFields":["customer_key"]}]}
                    """));
        when(jdbc.queryForList(contains("ac_external_table_snapshots"), eq(String.class),
                eq("child-release"), eq("customers"), eq("customers")))
                .thenReturn(List.of("{\"tableId\":41,\"tableName\":\"customers\",\"fields\":[]}"));
        var reader = new CatalogSnapshotReader(jdbc, new ObjectMapper());
        var form = Map.<String,Object>of("tableBindings",List.of(Map.of("bindingType","PRIMARY","tableName","orders")));
        var bindings = reader.formBindings("child-release", form);
        var fields = CatalogSnapshotReader.objects(bindings.get(0).get("fieldDefinitions"));
        assertThat(fields.get(0)).containsEntry("refTableId",41).containsEntry("refFunctionUnitCode","customers");
        verify(jdbc,never()).queryForList(contains("enabled=TRUE"),eq(String.class),anyString());
    }

    @Test void missingCapturedExternalTableIsExplicitlyRejected() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class), eq("child-release"), eq("DATA_TABLE")))
                .thenReturn(List.of("""
                    {"tableId":2,"tableName":"orders","fields":[{"fieldName":"customer_ref",
                    "isForeignKey":true,"refFunctionUnitCode":"customers","refTableName":"customers"}]}
                    """));
        var reader = new CatalogSnapshotReader(jdbc,new ObjectMapper());
        assertThatThrownBy(() -> reader.formBindings("child-release",Map.of("tableBindings",List.of(
                Map.of("bindingType","PRIMARY","tableName","orders"))))).isInstanceOf(FunctionUnitVersionException.class);
        verify(jdbc,never()).queryForList(contains("enabled=TRUE"),eq(String.class),anyString());
    }
    @Test void absentExternalSnapshotNeverUsesASameNamedInternalTable() {
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(),eq(String.class),eq("release"),eq("DATA_TABLE")))
                .thenReturn(List.of("{\"tableId\":2,\"tableName\":\"orders\",\"fields\":[{\"fieldName\":\"other_order\",\"isForeignKey\":true,\"refFunctionUnitCode\":\"another-fu\",\"refTableName\":\"orders\"}]}"));
        assertThatThrownBy(() -> new CatalogSnapshotReader(jdbc,new ObjectMapper()).formBindings("release",
                Map.of("tableBindings",List.of(Map.of("bindingType","PRIMARY","tableName","orders")))))
                .isInstanceOf(FunctionUnitVersionException.class);
    }
}
