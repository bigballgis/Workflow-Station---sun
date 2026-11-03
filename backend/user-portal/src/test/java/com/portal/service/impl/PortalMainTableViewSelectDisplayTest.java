package com.portal.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.list.ListColumnMeta;
import com.portal.component.FunctionUnitAccessComponent;
import com.portal.component.MainTableViewAccessResolver;
import com.portal.component.MainTableViewInvolvementScope;
import com.portal.component.MainTableViewRowQueryComponent;
import com.portal.component.MainTableViewSubRowQueryComponent;
import com.portal.dto.MainTableViewPortalDtos.MainTableViewDataPage;
import com.portal.dto.MainTableViewPortalDtos.MainTableViewFieldColumn;
import com.portal.dto.MainTableViewQueryRequest;
import com.portal.entity.ProcessInstance;
import com.portal.service.UserDisplayNameResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
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

    private final com.portal.client.PortalBiClient client = org.mockito.Mockito.mock(com.portal.client.PortalBiClient.class);
    private PortalMainTableViewServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        service = new PortalMainTableViewServiceImpl(
                client,
                new ObjectMapper(),
                mock(MainTableViewInvolvementScope.class),
                rowQueryComponent,
                mock(MainTableViewSubRowQueryComponent.class),
                mock(UserDisplayNameResolver.class));

        var fixture=new PublishedViewFixture();fixture.id=VIEW_ID;fixture.tableId=TABLE_ID;fixture.code="fu-merchant";
        var credit=PublishedViewFixture.field("merchant_credit",0);credit.put("displayLabel","Merchant Credit");credit.put("selectDisplay","label");
        var channel=PublishedViewFixture.field("channel",1);channel.put("displayLabel","Channel");
        fixture.definition.put("fields",List.of(credit,channel));
        fixture.forms.add(Map.of("configJson", """
                {"rule":[
                  {"type":"select","field":"merchant_credit","options":[{"label":"Y","value":"1"},{"label":"N","value":"2"}]},
                  {"type":"select","field":"channel","options":[{"label":"Web","value":"W"}]}
                ]}
                """, "tableBindings",List.of(Map.of("bindingId",521L,"bindingType","PRIMARY","tableName",fixture.tableName))));
        when(client.getDataView(USER_ID,VIEW_ID)).thenAnswer(call -> fixture.build());
        when(rowQueryComponent.query(any())).thenReturn(new MainTableViewRowQueryComponent.Page(
                List.of(ProcessInstance.builder()
                        .id("pi-1")
                        .variables(Map.of("merchant_credit", "1", "channel", "W"))
                        .build()),
                1L));
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
