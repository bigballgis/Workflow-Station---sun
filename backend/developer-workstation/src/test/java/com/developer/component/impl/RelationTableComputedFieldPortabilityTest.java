package com.developer.component.impl;

import com.developer.exception.DeveloperBusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FU packages carry referenced relation-table structure via JDBC. A computed column
 * on {@code rt_field_definitions} must round-trip; a computed flag without a formula
 * must fail rather than insert a blank {@code computed_field_json}.
 */
@ExtendWith(MockitoExtension.class)
class RelationTableComputedFieldPortabilityTest {

    @Mock private JdbcTemplate jdbcTemplate;

    private RelationTableStructurePortability portability;

    @BeforeEach
    void setUp() {
        portability = new RelationTableStructurePortability(jdbcTemplate, new ObjectMapper());
    }

    @Test
    void importAll_persistsComputedFieldJson() {
        when(jdbcTemplate.query(contains("FROM rt_table_definitions WHERE table_name"), any(RowMapper.class), eq("prices")))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForObject(contains("INSERT INTO rt_table_definitions"), eq(Long.class),
                any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(7L);

        Map<String, Object> formula = Map.of("source", "qty * price", "scope", "row");
        portability.importAll(List.of(Map.of(
                "tableName", "prices",
                "displayName", "Prices",
                "fields", List.of(Map.of(
                        "fieldName", "amount",
                        "dataType", "DECIMAL",
                        "isComputed", true,
                        "computedField", formula,
                        "sortOrder", 0)))), "tester");

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(contains("is_computed, computed_field_json"), args.capture());
        Object[] inserted = args.getValue();
        assertEquals(7L, inserted[0]);
        assertEquals("amount", inserted[1]);
        assertEquals(Boolean.TRUE, inserted[inserted.length - 2]);
        assertTrue(String.valueOf(inserted[inserted.length - 1]).contains("qty * price"));
    }

    @Test
    void importAll_rejectsComputedFlagWithoutFormula() {
        when(jdbcTemplate.query(contains("FROM rt_table_definitions WHERE table_name"), any(RowMapper.class), eq("prices")))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForObject(contains("INSERT INTO rt_table_definitions"), eq(Long.class),
                any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(7L);

        DeveloperBusinessException ex = assertThrows(DeveloperBusinessException.class, () ->
                portability.importAll(List.of(Map.of(
                        "tableName", "prices",
                        "fields", List.of(Map.of(
                                "fieldName", "amount",
                                "dataType", "DECIMAL",
                                "isComputed", true)))), "tester"));
        assertEquals("COMPUTED_FIELD_IMPORT_INVALID", ex.getErrorCode());
    }
}
