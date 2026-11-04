package com.portal.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.component.FunctionUnitAccessComponent;
import com.portal.component.MainTableViewAccessResolver;
import com.portal.component.MainTableViewInvolvementScope;
import com.portal.component.MainTableViewRowQueryComponent;
import com.portal.component.MainTableViewSubRowQueryComponent;
import com.portal.dto.MainTableViewQueryRequest;
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

import java.util.List;

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

    private final com.portal.client.PortalBiClient client = org.mockito.Mockito.mock(com.portal.client.PortalBiClient.class);
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
    }

    /** A published SUB view row as {@code loadPublishedView} selects it. */
    private void givenSubView(String tableName) {
        var fixture=new PublishedViewFixture(); fixture.id=VIEW_ID;fixture.tableName=tableName;
        fixture.tableType="SUB";fixture.code="fu-mi-demo";fixture.tableId=50331;
        when(client.getDataView("user-dev",VIEW_ID)).thenAnswer(call -> fixture.build());
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

        service.queryViewData("user-dev", VIEW_ID, new MainTableViewQueryRequest(0, 20, null, null, null, null, null));

        assertThat(capturedQuery().storeKey())
                .as("rows live under dw:<table name>; a binding id finds nothing and the view "
                        + "silently renders No Data")
                .isEqualTo("dw:subtable");
    }

    @Test
    void theFormBindingsAreNotQueriedAtAllAnyMore() {
        givenSubView("subtable");

        service.queryViewData("user-dev", VIEW_ID, new MainTableViewQueryRequest(0, 20, null, null, null, null, null));

        verify(jdbcTemplate, never())
                .queryForList(contains("dw_form_table_bindings"), eq(Long.class), anyLong());
    }

    @Test
    void aMixedCaseTableNameIsNormalisedToMatchTheStoredKey() {
        givenSubView("ATM_Transaction");

        service.queryViewData("user-dev", VIEW_ID, new MainTableViewQueryRequest(0, 20, null, null, null, null, null));

        assertThat(capturedQuery().storeKey())
                .as("the store lowercases table names, matching the lower() unique index")
                .isEqualTo("dw:atm_transaction");
    }
}
