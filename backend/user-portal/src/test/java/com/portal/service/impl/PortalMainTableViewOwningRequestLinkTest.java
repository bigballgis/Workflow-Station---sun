package com.portal.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.component.ComputedFieldRecalculator;
import com.portal.component.FunctionUnitAccessComponent;
import com.portal.component.MainTableViewAccessResolver;
import com.portal.component.MainTableViewInvolvementScope;
import com.portal.component.MainTableViewRowQueryComponent;
import com.portal.component.MainTableViewSubRowQueryComponent;
import com.portal.component.ProcessComponent;
import com.portal.dto.MainTableViewPortalDtos.MainTableViewFieldColumn;
import com.portal.dto.MainTableViewQueryRequest;
import com.portal.repository.ProcessInstanceRepository;
import com.portal.service.UserDisplayNameResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A SUB view's link back to its MAIN table points at the request the row lives in, so the column
 * says so ({@code refOwningRequest}) and the portal opens that request instead of listing the MAIN
 * view filtered by the cell text. A field-level FK names some other row and keeps the list drill-down.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortalMainTableViewOwningRequestLinkTest {

    private static final Long VIEW_ID = 50294L;
    private static final Long SUB_TABLE_ID = 50327L;
    private static final Long CASE_VIEW_ID = 50292L;

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

        Map<String, Object> view = new HashMap<>();
        view.put("id", VIEW_ID);
        view.put("view_name", "ATM Transaction");
        view.put("sort_config", null);
        view.put("filter_config", null);
        view.put("fu_code", "atm");
        view.put("main_table_id", SUB_TABLE_ID);
        view.put("table_type", "SUB");
        view.put("table_name", "ATM_Transaction");
        view.put("restrict_to_involved_users", Boolean.FALSE);
        when(jdbcTemplate.queryForList(contains("dw_main_table_view_configs"), eq(VIEW_ID)))
                .thenReturn(List.of(view));
        when(jdbcTemplate.queryForList(contains("dw_main_table_view_access"), eq(VIEW_ID)))
                .thenReturn(List.of());
        when(jdbcTemplate.query(contains("dw_main_table_view_fields"), any(RowMapper.class), eq(VIEW_ID)))
                .thenAnswer(inv -> {
                    RowMapper<Object> mapper = inv.getArgument(1);
                    return List.of(
                            mapper.mapRow(fieldRow("case_row_id", 0), 0),
                            mapper.mapRow(fieldRow("merchant_id", 1), 1));
                });

        Map<String, Object> merchantFk = new HashMap<>();
        merchantFk.put("field_name", "merchant_id");
        merchantFk.put("ref_pk_fields", "[\"merchant_code\"]");
        merchantFk.put("ref_view_id", 60001L);
        merchantFk.put("ref_fu_code", "merchants");

        Map<String, Object> caseFk = new HashMap<>();
        caseFk.put("field_name", "case_row_id");
        caseFk.put("ref_pk_fields", "[\"case_number\"]");
        caseFk.put("ref_view_id", CASE_VIEW_ID);
        caseFk.put("ref_fu_code", "atm");
        when(jdbcTemplate.queryForList(contains("fd.ref_primary_key_fields"), eq(VIEW_ID)))
                .thenReturn(List.of(caseFk, merchantFk));
        when(jdbcTemplate.queryForList(contains("filter_fk_field_id"), eq(String.class), eq(SUB_TABLE_ID)))
                .thenReturn(List.of("case_row_id"));

        when(accessResolver.parseAccessRules(any())).thenReturn(List.of());
        when(accessResolver.canUserSeeView(any(), any())).thenReturn(true);
        when(functionUnitAccessComponent.canAccessFunctionUnit(any(), any())).thenReturn(true);
        when(functionUnitAccessComponent.isFunctionUnitEnabled(any())).thenReturn(true);
        when(subRowQueryComponent.query(any()))
                .thenReturn(new MainTableViewSubRowQueryComponent.Page(List.of(), 0L));
    }

    private static ResultSet fieldRow(String fieldName, int sortOrder) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("field_name")).thenReturn(fieldName);
        when(rs.getString("display_label")).thenReturn(fieldName);
        when(rs.getInt("sort_order")).thenReturn(sortOrder);
        when(rs.getBoolean("visible")).thenReturn(true);
        when(rs.getString("column_type")).thenReturn("field");
        when(rs.getString("select_display")).thenReturn("value");
        return rs;
    }

    private Map<String, MainTableViewFieldColumn> columns() {
        return service.queryViewData("user-dev", VIEW_ID,
                        new MainTableViewQueryRequest(0, 20, null, null, null, null, null))
                .columns().stream()
                .collect(java.util.stream.Collectors.toMap(MainTableViewFieldColumn::fieldName, c -> c));
    }

    @Test
    void theLinkBackToTheMainTableOpensTheOwningRequest() {
        MainTableViewFieldColumn link = columns().get("case_row_id");

        assertThat(link.isForeignKey()).isTrue();
        assertThat(link.refViewId()).isEqualTo(CASE_VIEW_ID);
        assertThat(link.refOwningRequest()).isTrue();
    }

    @Test
    void aFieldLevelForeignKeyKeepsTheListDrillDown() {
        MainTableViewFieldColumn fk = columns().get("merchant_id");

        assertThat(fk.isForeignKey()).isTrue();
        assertThat(fk.refOwningRequest()).isFalse();
    }
}
