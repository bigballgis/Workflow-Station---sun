package com.portal.component;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

/** BU/Role grant cases now run against RuntimeDataViewAccessTest in Admin Center. */
class MainTableViewAccessResolverTest {
    @Test void administratorIdentityIsDelegatedAndMissingIdentityIsRejected() {
        var access=mock(com.portal.client.PortalBiClient.class);
        var resolver=new MainTableViewAccessResolver(access);
        when(access.isDataViewAdministrator("admin")).thenReturn(true);
        assertThat(resolver.isSystemAdministrator("admin")).isTrue();
        assertThat(resolver.isSystemAdministrator("reader")).isFalse();
        assertThat(resolver.isSystemAdministrator(null)).isFalse();
        assertThat(resolver.isSystemAdministrator(" ")).isFalse();
        verify(access, never()).isDataViewAdministrator(null);
        verify(access, never()).isDataViewAdministrator(" ");
    }
}
