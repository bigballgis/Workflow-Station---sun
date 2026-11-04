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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Which SUB view column links back to the owning request is read from the SUB table's declared FK
 * to the MAIN table, not from {@code dw_form_table_bindings.foreign_key_field}. On legacy bindings
 * that cache holds the sub-table's own primary key — FU atm-20260623-gaevus: ATM_Transaction's
 * binding stores {@code row_id}, the declared FK is {@code case_row_id} → ATM_Case.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortalMainTableViewOwningFkSourceTest {

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
        when(subRowQueryComponent.query(any()))
                .thenReturn(new MainTableViewSubRowQueryComponent.Page(List.of(), 0L));
    }

    private void viewShows(String... fieldNames) {
        var fields=new java.util.ArrayList<Map<String,Object>>();
        for(int i=0;i<fieldNames.length;i++) fields.add(PublishedViewFixture.field(fieldNames[i],i));
        fixture.definition.put("fields",fields);
    }

    private void declaredOwningFks(String... fieldNames) {
        fixture.table.put("fields",java.util.Arrays.stream(fieldNames)
                .map(name -> PublishedViewFixture.fk(name,"Case","case_number")).toList());
    }

    private Map<String, MainTableViewFieldColumn> columns() {
        return service.queryViewData("user-dev", VIEW_ID,
                        new MainTableViewQueryRequest(0, 20, null, null, null, null, null))
                .columns().stream()
                .collect(Collectors.toMap(MainTableViewFieldColumn::fieldName, c -> c));
    }

    @Test
    void theDeclaredForeignKeyLinksTheOwningRequestAndTheOwnPrimaryKeyDoesNot() {
        viewShows("row_id", "case_row_id");
        declaredOwningFks("case_row_id");

        Map<String, MainTableViewFieldColumn> columns = columns();

        assertThat(columns.get("case_row_id").refOwningRequest()).isTrue();
        assertThat(columns.get("case_row_id").refViewId()).isEqualTo(CASE_VIEW_ID);
        assertThat(columns.get("row_id").isForeignKey()).isFalse();
        assertThat(columns.get("row_id").refOwningRequest()).isFalse();
    }

    @Test
    void aViewThatHidesTheDeclaredForeignKeyHasNoOwningLink() {
        viewShows("row_id", "card_number");
        declaredOwningFks("case_row_id");

        assertThat(columns().values())
                .noneMatch(c -> Boolean.TRUE.equals(c.isForeignKey()) || Boolean.TRUE.equals(c.refOwningRequest()));
    }

    @Test
    void twoCandidateForeignKeysAreNotGuessedBetween() {
        viewShows("row_id", "case_row_id");
        declaredOwningFks("case_row_id", "reopened_case_row_id");

        MainTableViewFieldColumn caseRow = columns().get("case_row_id");

        assertThat(caseRow.isForeignKey()).isTrue();
        assertThat(caseRow.refOwningRequest()).isFalse();
    }
}
