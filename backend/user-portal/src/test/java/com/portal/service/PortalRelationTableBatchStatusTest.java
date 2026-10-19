package com.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.security.util.SecurityContextUtils;
import com.portal.component.RoleAccessComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("PortalRelationTableServiceImpl changeStatusBatch")
class PortalRelationTableBatchStatusTest {

    private static final Long TABLE_ID = 100L;
    private static final String USER_ID = "user1";

    private JdbcTemplate jdbcTemplate;
    private MockedStatic<SecurityContextUtils> securityContext;
    private PortalRelationTableService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        securityContext = Mockito.mockStatic(SecurityContextUtils.class);
        securityContext.when(SecurityContextUtils::getCurrentActiveRoleId).thenReturn(Optional.of("role-rw"));
        RoleAccessComponent roleAccess = mock(RoleAccessComponent.class);
        when(roleAccess.getUserBusinessRoles(USER_ID)).thenReturn(List.of(Map.of("id", "role-rw")));
        service = new PortalRelationTableServiceImpl(
                jdbcTemplate,
                roleAccess,
                new ObjectMapper(),
                mock(com.platform.common.fk.PrimaryKeyAllocationService.class),
                mock(com.portal.component.RelationTableComputedFieldRecalculator.class));
    }

    @AfterEach
    void tearDown() {
        securityContext.close();
    }

    private void grant(String level) {
        when(jdbcTemplate.query(contains("SELECT permission_level FROM rt_table_access"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(level));
    }

    private void row(String rowId, boolean exists) {
        when(jdbcTemplate.query(contains("AND row_id = ?"), any(RowMapper.class), eq(TABLE_ID), eq(rowId)))
                .thenReturn(exists ? List.of(Map.of("id", rowId)) : List.of());
    }

    @Test
    void readWriteRoleSetsEachDistinctRow() {
        grant("READ_WRITE");
        row("a", true);
        row("b", true);

        int updated = service.changeStatusBatch(TABLE_ID, USER_ID, List.of("a", "b", "a"), "inactive");

        assertThat(updated).isEqualTo(2);
        verify(jdbcTemplate, times(2)).update(contains("SET status = ?"), eq("INACTIVE"), anyString(), any(), eq(USER_ID),
                eq(TABLE_ID), anyString());
    }

    @Test
    void readOnlyRoleIsRejectedBeforeAnyWrite() {
        grant("READONLY");

        assertThatThrownBy(() -> service.changeStatusBatch(TABLE_ID, USER_ID, List.of("a"), "INACTIVE"))
                .isInstanceOf(AccessDeniedException.class);
        verify(jdbcTemplate, never()).update(contains("SET status = ?"), any(Object[].class));
    }

    @Test
    void missingRowFailsTheWholeBatch() {
        grant("READ_WRITE");
        row("a", true);
        row("gone", false);

        assertThatThrownBy(() -> service.changeStatusBatch(TABLE_ID, USER_ID, List.of("a", "gone"), "INACTIVE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gone");
    }
}
