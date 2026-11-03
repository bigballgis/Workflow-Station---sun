package com.portal.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.component.FunctionUnitAccessComponent;
import com.portal.component.MainTableViewAccessResolver;
import com.portal.component.MainTableViewInvolvementScope;
import com.portal.component.MainTableViewRowQueryComponent;
import com.portal.component.MainTableViewSubRowQueryComponent;
import com.portal.dto.MainTableViewPortalDtos.MainTableViewFieldColumn;
import com.portal.dto.MainTableViewQueryRequest;
import com.portal.service.UserDisplayNameResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

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

    private final com.portal.client.PortalBiClient client = org.mockito.Mockito.mock(com.portal.client.PortalBiClient.class);
    private final PublishedViewFixture fixture=new PublishedViewFixture();
    private PortalMainTableViewServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PortalMainTableViewServiceImpl(
                client,
                new ObjectMapper(),
                mock(MainTableViewInvolvementScope.class),
                mock(MainTableViewRowQueryComponent.class),
                subRowQueryComponent,
                mock(UserDisplayNameResolver.class));

        fixture.id=VIEW_ID;fixture.tableId=SUB_TABLE_ID;fixture.tableName="ATM_Transaction";fixture.tableType="SUB";fixture.code="atm";
        fixture.parent("Case",1L);fixture.bindings("Case",fixture.tableName);
        fixture.references.add(new com.platform.common.dataview.RuntimeDataView.Reference(CASE_VIEW_ID,"atm","Case",true));
        when(client.getDataView("user-dev",VIEW_ID)).thenAnswer(call -> fixture.build());
        var merchant=PublishedViewFixture.fk("merchant_id","Merchant","merchant_code");
        merchant.put("refFunctionUnitCode","merchants");
        fixture.table.put("fields",List.of(PublishedViewFixture.fk("case_row_id","Case","case_number"),
                merchant));
        fixture.references.add(new com.platform.common.dataview.RuntimeDataView.Reference(60001L,"merchants","Merchant",true));
        fixture.definition.put("fields",List.of(PublishedViewFixture.field("case_row_id",0),PublishedViewFixture.field("merchant_id",1)));
        when(subRowQueryComponent.query(any()))
                .thenReturn(new MainTableViewSubRowQueryComponent.Page(List.of(), 0L));
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
    @Test
    void owningRequestLinkDoesNotRequireTheMainViewGrant() {
        fixture.references.removeIf(ref -> ref.id().equals(CASE_VIEW_ID));
        MainTableViewFieldColumn link = columns().get("case_row_id");
        assertThat(link.isForeignKey()).isTrue();
        assertThat(link.refOwningRequest()).isTrue();
        assertThat(link.refViewId()).isNull();
        assertThat(columns().get("merchant_id").refOwningRequest()).isFalse();
    }
    @Test
    void aSameNamedExternalTableIsNeverAnOwningRequestLink() {
        var external=PublishedViewFixture.fk("case_row_id","Case","case_number");
        external.put("refFunctionUnitCode","other-fu");
        fixture.table.put("fields",List.of(external));
        fixture.references.clear();
        assertThat(columns().get("case_row_id").refOwningRequest()).isFalse();
    }
}
