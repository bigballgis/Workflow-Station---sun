package com.portal.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.client.PortalBiClient;
import com.portal.component.*;
import com.portal.service.UserDisplayNameResolver;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class PortalMainTableViewMenuIconTest {
    private final PortalBiClient client=mock(PortalBiClient.class);
    private final PortalMainTableViewServiceImpl service=new PortalMainTableViewServiceImpl(client,new ObjectMapper(),
            mock(MainTableViewInvolvementScope.class),mock(MainTableViewRowQueryComponent.class),
            mock(MainTableViewSubRowQueryComponent.class),mock(UserDisplayNameResolver.class));
    @Test void noAccessibleAdminViewsMeansNoMenu() {
        when(client.getDataViews("user")).thenReturn(List.of());
        assertThat(service.listAccessibleFunctionUnits("user")).isEmpty();
    }
    @Test void menuUsesOnlyAdminPublishedMetadataIncludingIcon() {
        var fixture=new PublishedViewFixture();fixture.code="fu-atm";
        when(client.getDataViews("user")).thenReturn(List.of(fixture.build()));
        var result=service.listAccessibleFunctionUnits("user");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).functionUnitCode()).isEqualTo("fu-atm");
        assertThat(result.get(0).iconSvg()).isEqualTo("<svg></svg>");
        assertThat(result.get(0).viewCount()).isEqualTo(1);
    }
    @Test void failedAdminDirectoryCannotBecomeAnEmptyOrDesignerMenu() {
        when(client.getDataViews("user")).thenThrow(new IllegalStateException("Admin unavailable"));
        assertThatThrownBy(() -> service.listAccessibleFunctionUnits("user")).hasMessage("Admin unavailable");
    }
}
