package com.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.component.RoleAccessComponent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("PortalRelationTableServiceImpl searchForLookup — INACTIVE rows")
class PortalRelationTableLookupInactiveTest {

    private static final Long TABLE_ID = 100L;

    /** Runs a lookup search and returns the SQL issued against rt_table_data_rows. */
    private String dataRowsSql(boolean includeInactive) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                argThat(sql -> sql != null && sql.contains("rt_table_definitions")),
                eq(Integer.class),
                any(Object[].class)))
                .thenReturn(1);
        when(jdbcTemplate.query(contains("SELECT field_name FROM rt_field_definitions"), any(RowMapper.class), eq(TABLE_ID)))
                .thenReturn(List.of("id", "name"));

        PortalRelationTableService service = new PortalRelationTableServiceImpl(
                jdbcTemplate,
                mock(RoleAccessComponent.class),
                new ObjectMapper(),
                mock(com.platform.common.fk.PrimaryKeyAllocationService.class),
                mock(com.portal.component.RelationTableComputedFieldRecalculator.class));
        service.searchForLookup(TABLE_ID, "acme", List.of("name"), "name", null, 20, 0, includeInactive);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
        return sqlCaptor.getAllValues().stream()
                .filter(sql -> sql.contains("rt_table_data_rows"))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void keywordSearchAndDropdownExcludeInactiveRows() {
        assertThat(dataRowsSql(false)).contains("status = 'ACTIVE'");
    }

    @Test
    void savedValueResolutionStillFindsInactiveRows() {
        assertThat(dataRowsSql(true)).doesNotContain("status");
    }
}
