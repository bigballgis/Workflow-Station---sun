package com.developer.component.impl;

import com.developer.exception.DeveloperBusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A relation table another Function Unit also uses has its own versions; a Function Unit only references it, so
 * importing or rolling back the Function Unit restores the Function Unit alone and never reshapes the table —
 * as long as the table still has the columns the restored version uses.
 */
@ExtendWith(MockitoExtension.class)
class RelationTableSharedImportTest {

    @Mock private JdbcTemplate jdbcTemplate;

    private RelationTableStructurePortability portability;

    @BeforeEach
    void setUp() {
        portability = new RelationTableStructurePortability(jdbcTemplate, new ObjectMapper());
        when(jdbcTemplate.query(contains("FROM rt_table_definitions WHERE table_name"), any(RowMapper.class), eq("orders")))
                .thenReturn(List.of(5L));
    }

    private void linkedTo(String... codes) {
        when(jdbcTemplate.query(contains("FROM rt_table_function_units"), any(RowMapper.class), eq(5L)))
                .thenReturn(List.of(codes));
    }

    /** The table as it now is: alternating column name and type. */
    private void columnsNow(String... nameAndType) {
        when(jdbcTemplate.query(contains("FROM rt_field_definitions f WHERE f.table_id"), any(RowMapper.class), eq(5L)))
                .thenReturn(java.util.stream.IntStream.range(0, nameAndType.length / 2)
                        .mapToObj(i -> Map.of("fieldName", nameAndType[2 * i], "dataType", nameAndType[2 * i + 1]))
                        .toList());
    }

    /** The table as the restored or imported version has it. */
    private static Map<String, Object> version(String... nameAndType) {
        List<Map<String, Object>> fields = java.util.stream.IntStream.range(0, nameAndType.length / 2)
                .mapToObj(i -> Map.<String, Object>of("fieldName", nameAndType[2 * i], "dataType", nameAndType[2 * i + 1]))
                .toList();
        return Map.of("tableName", "orders", "displayName", "Orders", "description", "Order records", "fields", fields);
    }

    @Test
    void sharedTableThatGainedColumnsIsKeptAndTheFunctionUnitStillRestores() {
        linkedTo("other-fu");
        columnsNow("code", "VARCHAR", "amount", "DECIMAL", "added_later", "VARCHAR");

        assertEquals(Map.of("orders", 5L), portability.importAll(
                List.of(version("code", "VARCHAR", "amount", "DECIMAL")), "tester", "this-fu"));
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    /** Being linked itself does not make a table another Function Unit uses this one's to reshape. */
    @Test
    void tableAlsoUsedByThisFunctionUnitIsKeptToo() {
        linkedTo("this-fu", "other-fu");
        columnsNow("code", "VARCHAR", "added_later", "VARCHAR");

        portability.importAll(List.of(version("code", "VARCHAR")), "tester", "this-fu");

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void sharedTableMissingOrRetypingAColumnTheVersionUsesRefusesIt() {
        linkedTo("other-fu");
        columnsNow("code", "INTEGER");

        DeveloperBusinessException refused = assertThrows(DeveloperBusinessException.class, () -> portability.importAll(
                List.of(version("code", "VARCHAR", "amount", "DECIMAL")), "tester", "this-fu"));

        assertEquals("FU_IMPORT_RT_SHARED_INCOMPATIBLE", refused.getErrorCode());
        assertTrue(Arrays.stream(new String[] {"code (VARCHAR -> INTEGER)", "amount (missing)"})
                .allMatch(refused.getMessage()::contains), refused.getMessage());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void tableOnlyThisFunctionUnitUsesIsStillRestoredToTheVersion() {
        linkedTo("this-fu");
        columnsNow("code", "VARCHAR", "added_later", "VARCHAR");
        when(jdbcTemplate.queryForMap(contains("SELECT display_name, description"), eq(5L)))
                .thenReturn(Map.of("display_name", "Orders", "description", "Order records"));

        portability.importAll(List.of(version("code", "VARCHAR")), "tester", "this-fu");

        verify(jdbcTemplate).update(contains("status = 'UPDATED'"), eq("Orders"), eq("Order records"),
                any(), eq("tester"), eq(5L));
        verify(jdbcTemplate).update(contains("DELETE FROM rt_field_definitions"), eq(5L));
    }
}
