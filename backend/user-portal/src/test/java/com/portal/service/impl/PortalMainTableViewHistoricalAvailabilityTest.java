package com.portal.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.functionunit.CatalogSnapshotReader;
import com.portal.component.*;
import com.portal.client.PortalBiClient;
import com.portal.dto.MainTableViewQueryRequest;
import com.portal.entity.ProcessInstance;
import com.portal.service.UserDisplayNameResolver;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PortalMainTableViewHistoricalAvailabilityTest {
    @Test void laterViewKeepsOldRowsButExplicitlyMarksTheirDetailUnavailable() {
        var client=mock(PortalBiClient.class);
        var rows=mock(MainTableViewSubRowQueryComponent.class);
        var jdbc=mock(JdbcTemplate.class);
        var service=new PortalMainTableViewServiceImpl(client,new ObjectMapper(),mock(MainTableViewInvolvementScope.class),
                mock(MainTableViewRowQueryComponent.class),rows,mock(UserDisplayNameResolver.class));
        var fixture=new PublishedViewFixture();fixture.tableType="SUB";fixture.definition.put("detailFormId",77);
        when(client.getDataView("reader",fixture.id)).thenAnswer(call -> fixture.build());
        var instance=new ProcessInstance();instance.setId("old");instance.setFunctionUnitCode(fixture.code);
        instance.setFunctionUnitCatalogId("old-release");instance.setVariables(Map.of());
        when(rows.query(any())).thenReturn(new MainTableViewSubRowQueryComponent.Page(List.of(
                new MainTableViewSubRowQueryComponent.Row(instance,Map.of("platformRowUuid","line-1"))),1));
        when(jdbc.queryForList(anyString(),eq(String.class),eq("old-release"),eq("MAIN_TABLE_VIEW")))
                .thenReturn(List.of("[{\"viewKey\":\"another-key\",\"viewName\":\"Earlier\",\"mainTableName\":\"main\"}]"));
        ReflectionTestUtils.setField(service,"versionSnapshots",new CatalogSnapshotReader(jdbc,new ObjectMapper()));
        var page=service.queryViewData("reader",fixture.id,new MainTableViewQueryRequest(0,20,null,null,null,null,null));
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.rows()).singleElement().satisfies(row -> assertThat(row.detailAvailable()).isFalse());
        var detail=service.queryViewData("reader",fixture.id,new MainTableViewQueryRequest(0,1,null,null,null,null,"old|platformRowUuid=line-1"));
        assertThat(detail.rows()).singleElement().satisfies(row -> {
            assertThat(row.detailAvailable()).isFalse();assertThat(row.detailForm()).isEmpty();
        });
    }
}
