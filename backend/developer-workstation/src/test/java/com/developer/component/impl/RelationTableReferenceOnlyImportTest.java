package com.developer.component.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * A foreign key's parent table travels in a package (and a version snapshot) only as a reference: importing
 * or rolling back the Function Unit must not rewrite a table another Function Unit may own.
 */
@ExtendWith(MockitoExtension.class)
class RelationTableReferenceOnlyImportTest {

    @Mock private JdbcTemplate jdbcTemplate;

    private RelationTableStructurePortability portability;

    @BeforeEach
    void setUp() {
        portability = new RelationTableStructurePortability(jdbcTemplate, new ObjectMapper());
    }

    private static Map<String, Object> dropdown(String displayName) {
        return Map.of("tableName", "dropdown", "displayName", displayName,
                RelationTableStructurePortability.REFERENCE_ONLY, true,
                "fields", List.of(Map.of("fieldName", "id", "dataType", "VARCHAR", "isPrimaryKey", true)));
    }

    @Test
    void existingReferencedTableIsLeftAsItIs() {
        when(jdbcTemplate.query(contains("FROM rt_table_definitions WHERE table_name"), any(RowMapper.class), eq("dropdown")))
                .thenReturn(List.of(14L));

        Map<String, Long> ids = portability.importAll(List.of(dropdown("Changed elsewhere")), "tester", "fu-test");

        assertEquals(Map.of("dropdown", 14L), ids);
        verify(jdbcTemplate).query(contains("FROM rt_table_definitions WHERE table_name"), any(RowMapper.class), eq("dropdown"));
        verifyNoMoreInteractions(jdbcTemplate);
    }

    @Test
    void missingReferencedTableIsCreated() {
        when(jdbcTemplate.query(contains("FROM rt_table_definitions WHERE table_name"), any(RowMapper.class), eq("dropdown")))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForObject(contains("INSERT INTO rt_table_definitions"), eq(Long.class),
                eq("dropdown"), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(30L);

        assertEquals(Map.of("dropdown", 30L), portability.importAll(List.of(dropdown("Dropdown")), "tester", "fu-test"));
        verify(jdbcTemplate).update(contains("INSERT INTO rt_field_definitions"), any(Object[].class));
    }

    @Test
    void ownTableForeignKeyPointsAtTheExistingReferencedTable() {
        when(jdbcTemplate.query(contains("FROM rt_table_definitions WHERE table_name"), any(RowMapper.class), eq("dropdown")))
                .thenReturn(List.of(14L));
        when(jdbcTemplate.query(contains("FROM rt_table_definitions WHERE table_name"), any(RowMapper.class), eq("room")))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForObject(contains("INSERT INTO rt_table_definitions"), eq(Long.class),
                eq("room"), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(7L);
        Map<String, Object> room = Map.of("tableName", "room", "fields", List.of(
                Map.of("fieldName", "building", "dataType", "VARCHAR", "isForeignKey", true, "refTableName", "dropdown")));

        portability.importAll(List.of(room, dropdown("Dropdown")), "tester", "fu-test");

        verify(jdbcTemplate).update(contains("SET ref_table_id"), eq(14L), eq(7L), eq("building"));
        verify(jdbcTemplate, never()).update(contains("DELETE FROM rt_field_definitions"), eq(14L));
    }
}
