package com.portal.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.client.PortalBiClient;
import com.portal.component.MainTableViewInvolvementScope;
import com.portal.component.MainTableViewRowQueryComponent;
import com.portal.component.MainTableViewSubRowQueryComponent;
import com.portal.service.UserDisplayNameResolver;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PortalMainTableViewExportOnlyTest {
    @ParameterizedTest @ValueSource(strings={"MAIN","SUB"})
    void legacyImportConfigurationCannotAdvertiseAWriteCapability(String tableType) {
        var fixture=new PublishedViewFixture();fixture.tableType=tableType;
        fixture.definition.put("filterConfig",java.util.Map.of("toolbar",java.util.Map.of("enableImport",true,"enableExport",true)));
        var client=mock(PortalBiClient.class);
        when(client.getDataViews("reader")).thenReturn(List.of(fixture.build()));
        var service=new PortalMainTableViewServiceImpl(client,new ObjectMapper(),mock(MainTableViewInvolvementScope.class),
                mock(MainTableViewRowQueryComponent.class),mock(MainTableViewSubRowQueryComponent.class),mock(UserDisplayNameResolver.class));
        var summary=service.listPublishedViews("reader",fixture.code).get(0);
        assertThat(summary.enableImport()).isFalse();
        assertThat(summary.enableExport()).isTrue();
    }
}
