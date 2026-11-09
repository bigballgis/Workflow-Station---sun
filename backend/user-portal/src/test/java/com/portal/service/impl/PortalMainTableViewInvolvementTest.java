package com.portal.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.dto.UserPrincipal;
import com.portal.component.ComputedFieldRecalculator;
import com.portal.component.FunctionUnitAccessComponent;
import com.portal.component.MainTableViewAccessResolver;
import com.portal.component.MainTableViewInvolvementScope;
import com.portal.component.MainTableViewRowQueryComponent;
import com.portal.component.MainTableViewSubRowQueryComponent;
import com.portal.component.ProcessComponent;
import com.portal.dto.MainTableViewQueryRequest;
import com.portal.repository.ProcessInstanceRepository;
import com.portal.service.UserDisplayNameResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Platform access model D-05: a System Administrator who opens a view through a CUSTOM-group
 * session is judged like anyone — by the current BU + Role for visibility, and by involvement for
 * the rows of a "participants only" view.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortalMainTableViewInvolvementTest {

    private static final Long VIEW_ID = 9L;
    private static final Long TABLE_ID = 90L;
    private static final String FU_CODE = "fu-cases";
    private static final String ADMIN = "user-admin";

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private FunctionUnitAccessComponent functionUnitAccessComponent;
    @Mock
    private MainTableViewInvolvementScope involvementScope;
    @Mock
    private MainTableViewRowQueryComponent rowQueryComponent;

    private PortalMainTableViewServiceImpl service;
    private final Map<String, Object> viewRow = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        service = new PortalMainTableViewServiceImpl(
                jdbcTemplate,
                new ObjectMapper(),
                functionUnitAccessComponent,
                new MainTableViewAccessResolver(),
                involvementScope,
                rowQueryComponent,
                mock(MainTableViewSubRowQueryComponent.class),
                mock(ProcessInstanceRepository.class),
                mock(ProcessComponent.class),
                mock(ComputedFieldRecalculator.class),
                mock(UserDisplayNameResolver.class));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                UserPrincipal.builder().userId(ADMIN).username(ADMIN)
                        .roles(List.of("SYS_ADMIN")).superAdmin(true)
                        .activeBusinessUnitId("bu-1").activeRoleId("role-1").build(),
                null, List.of()));

        viewRow.put("id", VIEW_ID);
        viewRow.put("view_name", "Cases");
        viewRow.put("sort_config", null);
        viewRow.put("filter_config", null);
        viewRow.put("fu_code", FU_CODE);
        viewRow.put("main_table_id", TABLE_ID);
        viewRow.put("table_type", "MAIN");
        viewRow.put("restrict_to_involved_users", Boolean.TRUE);
        when(jdbcTemplate.queryForList(contains("dw_main_table_view_configs"), eq(VIEW_ID)))
                .thenReturn(List.of(viewRow));
        when(jdbcTemplate.query(contains("dw_main_table_view_fields"), any(RowMapper.class), eq(VIEW_ID)))
                .thenAnswer(inv -> {
                    RowMapper<Object> mapper = inv.getArgument(1);
                    return List.of(mapper.mapRow(fieldRow("case_number", "Case Number"), 0));
                });
        when(functionUnitAccessComponent.canAccessFunctionUnit(any(), any())).thenReturn(true);
        when(functionUnitAccessComponent.isFunctionUnitEnabled(any())).thenReturn(true);
        when(rowQueryComponent.query(any())).thenReturn(new MainTableViewRowQueryComponent.Page(List.of(), 0L));
    }

    @AfterEach
    void clearSession() {
        SecurityContextHolder.clearContext();
    }

    private void accessRules(List<Map<String, Object>> rows) {
        when(jdbcTemplate.queryForList(contains("dw_main_table_view_access"), eq(VIEW_ID))).thenReturn(rows);
    }

    private static ResultSet fieldRow(String fieldName, String label) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("field_name")).thenReturn(fieldName);
        when(rs.getString("display_label")).thenReturn(label);
        when(rs.getObject("column_width")).thenReturn(null);
        when(rs.getInt("sort_order")).thenReturn(0);
        when(rs.getBoolean("visible")).thenReturn(true);
        when(rs.getBoolean("is_system_field")).thenReturn(false);
        when(rs.getString("column_type")).thenReturn("field");
        return rs;
    }

    private static MainTableViewQueryRequest firstPage() {
        return new MainTableViewQueryRequest(0, 20, null, null, null, null, null);
    }

    /** "Participants only" no longer shows a System Administrator every row. */
    @Test
    void participantsOnlyViewLimitsSystemAdministratorToInvolvedRows() {
        accessRules(List.of(
                Map.of("target_type", "BUSINESS_UNIT", "target_id", "bu-1"),
                Map.of("target_type", "ROLE", "target_id", "role-1")));
        MainTableViewInvolvementScope.Predicate involved =
                new MainTableViewInvolvementScope.Predicate(" AND pi.start_user_id = ?", List.of(ADMIN));
        when(involvementScope.predicateFor(ADMIN, FU_CODE)).thenReturn(involved);

        service.queryViewData(ADMIN, VIEW_ID, firstPage());

        ArgumentCaptor<MainTableViewRowQueryComponent.Query> query =
                ArgumentCaptor.forClass(MainTableViewRowQueryComponent.Query.class);
        verify(rowQueryComponent).query(query.capture());
        assertThat(query.getValue().involvement()).isSameAs(involved);
    }

    /** An empty access config is visible to nobody, a System Administrator included. */
    @Test
    void emptyAccessConfigRefusesSystemAdministrator() {
        accessRules(List.of());

        assertThatThrownBy(() -> service.queryViewData(ADMIN, VIEW_ID, firstPage()))
                .isInstanceOf(FunctionUnitAccessComponent.FunctionUnitAccessDeniedException.class);
        verify(rowQueryComponent, never()).query(any());
    }
}
