package com.platform.common.jdbc;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SubTableRowKeySupportOpaqueKeyTest {
    @Test
    void doesNotNumericallyInspectAnUnrelatedConfiguredPrimaryKey() {
        AtomicInteger inspections = new AtomicInteger();
        Number key = observedKey(inspections);
        Map<String, Object> result = SubTableRowKeySupport.rowKeyFromVariableRow(
                Map.of("custom_key", key), List.of("custom_key"));
        assertSame(key, result.get("custom_key"));
        assertEquals(0, inspections.get(), "Opaque configured identities must not enter numeric alias substitution");
    }

    @Test
    void doesNotNumericallyInspectAnUnrelatedNestedPrimaryKey() {
        AtomicInteger inspections = new AtomicInteger();
        Number key = observedKey(inspections);
        Map<String, Object> result = SubTableRowKeySupport.rowKeyFromVariableRow(
                Map.of("rowKey", Map.of("custom_key", key)), List.of("custom_key"));
        assertSame(key, result.get("custom_key"));
        assertEquals(0, inspections.get());
    }

    @Test
    void preservesManualAndCompositeKeysWithoutInferringAliases() {
        String key = "synthetic-uuid-primary-key";
        assertEquals(Map.of("custom_key", key), SubTableRowKeySupport.rowKeyFromVariableRow(
                Map.of("custom_key", key, "rowId", 99), List.of("custom_key")));
        assertEquals(Map.of("part_a", "A", "part_b", "B"), SubTableRowKeySupport.rowKeyFromVariableRow(
                Map.of("part_a", "A", "part_b", "B"), List.of("part_a", "part_b")));
    }

    @Test
    void retainsNumericAliasSubstitutionForConfiguredIdColumns() {
        assertEquals(Map.of("id", 42L), SubTableRowKeySupport.rowKeyFromVariableRow(
                Map.of("id", "placeholder", "id_idw", 42L), List.of("id")));
        assertEquals(Map.of("id_idw", 42L), SubTableRowKeySupport.rowKeyFromVariableRow(
                Map.of("id_idw", "placeholder", "id", 42L), List.of("id_idw")));
        assertEquals(Map.of("id", 42L), SubTableRowKeySupport.rowKeyFromVariableRow(
                Map.of("id", 42L, "rowKey", Map.of("id", 1L)), List.of("id")));
    }

    private static Number observedKey(AtomicInteger inspections) {
        return new Number() {
            @Override public int intValue() { return 42; }
            @Override public long longValue() { return 42L; }
            @Override public float floatValue() { return 42F; }
            @Override public double doubleValue() { inspections.incrementAndGet(); return 42D; }
        };
    }
}
