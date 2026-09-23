package com.portal.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.list.ListColumnMeta;
import com.portal.component.ComputedFieldRecalculator;
import com.portal.component.FunctionUnitAccessComponent;
import com.portal.component.MainTableViewAccessResolver;
import com.portal.component.MainTableViewInvolvementScope;
import com.portal.component.MainTableViewRowQueryComponent;
import com.portal.component.MainTableViewSubRowQueryComponent;
import com.portal.component.ProcessComponent;
import com.portal.dto.MainTableViewPortalDtos.MainTableViewDataPage;
import com.portal.dto.MainTableViewPortalDtos.MainTableViewFieldColumn;
import com.portal.dto.MainTableViewQueryRequest;
import com.portal.entity.ProcessInstance;
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

import java.nio.charset.StandardCharsets;
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
 * A view column designed with {@code select_display = label} carries the bound form widget's
 * static options so the portal can show labels, and the server CSV export writes those labels.
 * Row values themselves stay stored values.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortalMainTableViewSelectDisplayTest {

    private static final Long VIEW_ID = 7L;
    private static final Long TABLE_ID = 70L;
    private static final String USER_ID = "user-dev";

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private FunctionUnitAccessComponent functionUnitAccessComponent;
    @Mock
    private MainTableViewAccessResolver accessResolver;
    @Mock
    private MainTableViewRowQueryComponent rowQueryComponent;

    private PortalMainTableViewServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        service = new PortalMainTableViewServiceImpl(
                jdbcTemplate,
                new ObjectMapper(),
                functionUnitAccessComponent,
                accessResolver,
                mock(MainTableViewInvolvementScope.class),
                rowQueryComponent,
                mock(MainTableViewSubRowQueryComponent.class),
                mock(ProcessInstanceRepository.class),
                mock(ProcessComponent.class),
                mock(ComputedFieldRecalculator.class),
                mock(UserDisplayNameResolver.class));

        Map<String, Object> viewRow = new HashMap<>();
        viewRow.put("id", VIEW_ID);
        viewRow.put("view_name", "Merchants");
        viewRow.put("sort_config", null);
        viewRow.put("filter_config", null);
        viewRow.put("fu_code", "fu-merchant");
        viewRow.put("main_table_id", TABLE_ID);
        viewRow.put("table_type", "MAIN");
        viewRow.put("restrict_to_involved_users", Boolean.FALSE);
        when(jdbcTemplate.queryForList(contains("dw_main_table_view_configs"), eq(VIEW_ID)))
                .thenReturn(List.of(viewRow));
        when(jdbcTemplate.queryForList(contains("dw_main_table_view_access"), eq(VIEW_ID)))
                .thenReturn(List.of());
        when(jdbcTemplate.query(contains("dw_main_table_view_fields"), any(RowMapper.class), eq(VIEW_ID)))
                .thenAnswer(inv -> {
                    RowMapper<Object> mapper = inv.getArgument(1);
                    return List.of(
                            mapper.mapRow(fieldRow("merchant_credit", "Merchant Credit", 0, "label"), 0),
                            mapper.mapRow(fieldRow("channel", "Channel", 1, "value"), 1));
                });
        // The form's PRIMARY binding on this table contributes its top-level rules.
        Map<String, Object> primaryBinding = new HashMap<>();
        primaryBinding.put("binding_id", 521L);
        primaryBinding.put("binding_type", "PRIMARY");
        primaryBinding.put("cfg", """
                {"rule":[
                  {"type":"select","field":"merchant_credit","options":[{"label":"Y","value":"1"},{"label":"N","value":"2"}]},
                  {"type":"select","field":"channel","options":[{"label":"Web","value":"W"}]}
                ]}
                """);
        when(jdbcTemplate.queryForList(contains("b.binding_type"), eq(TABLE_ID)))
                .thenReturn(List.of(primaryBinding));
        when(accessResolver.parseAccessRules(any())).thenReturn(List.of());
        when(accessResolver.canUserSeeView(any(), any())).thenReturn(true);
        when(functionUnitAccessComponent.canAccessFunctionUnit(any(), any())).thenReturn(true);
        when(functionUnitAccessComponent.isFunctionUnitEnabled(any())).thenReturn(true);
        when(functionUnitAccessComponent.isSystemAdministrator(any())).thenReturn(true);
        when(rowQueryComponent.query(any())).thenReturn(new MainTableViewRowQueryComponent.Page(
                List.of(ProcessInstance.builder()
                        .id("pi-1")
                        .variables(Map.of("merchant_credit", "1", "channel", "W"))
                        .build()),
                1L));
    }

    private static ResultSet fieldRow(String fieldName, String label, int sortOrder, String selectDisplay)
            throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("field_name")).thenReturn(fieldName);
        when(rs.getString("display_label")).thenReturn(label);
        when(rs.getObject("column_width")).thenReturn(null);
        when(rs.getInt("sort_order")).thenReturn(sortOrder);
        when(rs.getBoolean("visible")).thenReturn(true);
        when(rs.getBoolean("is_system_field")).thenReturn(false);
        when(rs.getString("column_type")).thenReturn("field");
        when(rs.getString("select_display")).thenReturn(selectDisplay);
        return rs;
    }

    private static MainTableViewQueryRequest firstPage() {
        return new MainTableViewQueryRequest(0, 20, null, null, null, null, null);
    }

    @Test
    void labelColumnCarriesItsOptionsWhileRowsKeepStoredValues() {
        MainTableViewDataPage page = service.queryViewData(USER_ID, VIEW_ID, firstPage());

        MainTableViewFieldColumn credit = page.columns().get(0);
        assertThat(credit.selectDisplay()).isEqualTo("label");
        assertThat(credit.selectOptions()).containsExactly(
                new ListColumnMeta.Option("1", "Y"), new ListColumnMeta.Option("2", "N"));

        MainTableViewFieldColumn channel = page.columns().get(1);
        assertThat(channel.selectDisplay()).isEqualTo("value");
        assertThat(channel.selectOptions()).isNull();

        assertThat(page.rows().get(0).values()).containsEntry("merchant_credit", "1");
    }

    @Test
    void csvExportWritesLabelsForLabelColumnsOnly() {
        byte[] csv = service.exportViewCsv(USER_ID, VIEW_ID, 100, firstPage());

        String text = new String(csv, StandardCharsets.UTF_8);
        assertThat(text).isEqualTo("processInstanceId,Merchant Credit,Channel\npi-1,Y,W\n");
    }
}
