package com.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.component.RoleAccessComponent;
import com.portal.dto.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("PortalRelationTableServiceImpl queryTableData search")
class PortalRelationTableQueryDataSearchTest {

    @Test
    void queryTableData_appliesSearchFilterOnJsonRows() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RoleAccessComponent roleAccess = mock(RoleAccessComponent.class);
        ObjectMapper objectMapper = new ObjectMapper();

        Long tableId = 100L;
        String userId = "user1";

        when(roleAccess.getUserBusinessRoles(userId)).thenReturn(List.of(Map.of("id", "role1")));
        when(jdbcTemplate.queryForList(contains("sys_user_business_unit_roles"), eq(String.class), eq(userId)))
                .thenReturn(Collections.emptyList());
        when(jdbcTemplate.queryForObject(
                argThat(sql -> sql.contains("rt_table_access")),
                eq(Long.class),
                any(Object[].class)))
                .thenReturn(1L);
        when(jdbcTemplate.queryForObject(
                argThat(sql -> sql.contains("rt_table_definitions")),
                eq(Integer.class),
                any()))
                .thenReturn(1);
        when(jdbcTemplate.query(contains("rt_field_definitions"), any(RowMapper.class), eq(tableId)))
                .thenReturn(List.of("name", "code"));
        when(jdbcTemplate.queryForObject(
                argThat(sql -> sql.contains("COUNT(*)") && sql.contains("ILIKE")),
                eq(Long.class),
                any(Object[].class)))
                .thenReturn(1L);
        when(jdbcTemplate.query(
                argThat(sql -> sql.contains("SELECT data") && sql.contains("ILIKE")),
                any(RowMapper.class),
                any(Object[].class)))
                .thenReturn(Collections.emptyList());

        PortalRelationTableService service = new PortalRelationTableServiceImpl(jdbcTemplate, roleAccess, objectMapper,
                mock(com.platform.common.fk.PrimaryKeyAllocationService.class));
        PageResponse<Map<String, Object>> result = service.queryTableData(tableId, userId, 0, 10, "acme");

        assertThat(result.getTotalElements()).isEqualTo(1L);

        ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(countSql.capture(), eq(Long.class), any(Object[].class));
        assertThat(countSql.getValue()).contains("data->>'name'").contains("data->>'code'");
    }
}
