package com.portal.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Which {@code __subTables__} slice a SUB view reads.
 *
 * <p>Rows are stored under one canonical key per table ({@code dw:<table name>}). This used to
 * resolve the slice by querying {@code dw_form_table_bindings} for binding ids, which no writer
 * produces any more: the lookup found nothing and every SUB view silently showed "No Data".
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortalMainTableViewSubStoreKeyTest {

    private static final Long VIEW_ID = 50300L;

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private FunctionUnitAccessComponent functionUnitAccessComponent;
    @Mock
    private MainTableViewAccessResolver accessResolver;
    @Mock
    private MainTableViewSubRowQueryComponent subRowQueryComponent;

    private PortalMainTableViewServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PortalMainTableViewServiceImpl(
                jdbcTemplate,
                new ObjectMapper(),
                functionUnitAccessComponent,
                accessResolver,
                mock(MainTableViewInvolvementScope.class),
                mock(MainTableViewRowQueryComponent.class),
                subRowQueryComponent,
                mock(ProcessInstanceRepository.class),
                mock(ProcessComponent.class),
                mock(ComputedFieldRecalculator.class),
                mock(UserDisplayNameResolver.class));
    }

    /** A published SUB view row as {@code loadPublishedView} selects it. */
    private void givenSubView(String tableName) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", VIEW_ID);
        row.put("view_name", "Participants");
        row.put("sort_config", null);
        row.put("filter_config", null);
        row.put("fu_code", "fu-mi-demo");
        row.put("main_table_id", 50331L);
        row.put("table_type", "SUB");
        row.put("table_name", tableName);
        row.put("restrict_to_involved_users", Boolean.FALSE);

        when(jdbcTemplate.queryForList(contains("dw_main_table_view_configs"), eq(VIEW_ID)))
                .thenReturn(List.of(row));
        when(jdbcTemplate.query(contains("dw_main_table_view_fields"), any(org.springframework.jdbc.core.RowMapper.class), eq(VIEW_ID)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(contains("dw_main_table_view_access"), eq(VIEW_ID)))
                .thenReturn(List.of());
        when(accessResolver.parseAccessRules(any())).thenReturn(List.of());
        when(accessResolver.canUserSeeView(any(), any())).thenReturn(true);
        when(functionUnitAccessComponent.canAccessFunctionUnit(any(), any())).thenReturn(true);
        when(functionUnitAccessComponent.isFunctionUnitEnabled(any())).thenReturn(true);
        when(subRowQueryComponent.query(any()))
                .thenReturn(new MainTableViewSubRowQueryComponent.Page(List.of(), 0L));
    }

    private MainTableViewSubRowQueryComponent.Query capturedQuery() {
        ArgumentCaptor<MainTableViewSubRowQueryComponent.Query> captor =
                ArgumentCaptor.forClass(MainTableViewSubRowQueryComponent.Query.class);
        verify(subRowQueryComponent).query(captor.capture());
        return captor.getValue();
    }

    @Test
    void theSliceKeyComesFromTheTableNameRatherThanItsFormBindings() {
        givenSubView("subtable");

        service.queryViewData("user-dev", VIEW_ID, new MainTableViewQueryRequest(0, 20, null, null, null, null));

        assertThat(capturedQuery().storeKey())
                .as("rows live under dw:<table name>; a binding id finds nothing and the view "
                        + "silently renders No Data")
                .isEqualTo("dw:subtable");
    }

    @Test
    void theFormBindingsAreNotQueriedAtAllAnyMore() {
        givenSubView("subtable");

        service.queryViewData("user-dev", VIEW_ID, new MainTableViewQueryRequest(0, 20, null, null, null, null));

        verify(jdbcTemplate, never())
                .queryForList(contains("dw_form_table_bindings"), eq(Long.class), anyLong());
    }

    @Test
    void aMixedCaseTableNameIsNormalisedToMatchTheStoredKey() {
        givenSubView("ATM_Transaction");

        service.queryViewData("user-dev", VIEW_ID, new MainTableViewQueryRequest(0, 20, null, null, null, null));

        assertThat(capturedQuery().storeKey())
                .as("the store lowercases table names, matching the lower() unique index")
                .isEqualTo("dw:atm_transaction");
    }
}
