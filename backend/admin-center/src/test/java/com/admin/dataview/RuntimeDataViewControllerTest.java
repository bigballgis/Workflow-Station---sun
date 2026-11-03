package com.admin.dataview;

import com.admin.config.InternalServiceAuthenticator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import com.platform.common.constant.PlatformConstants;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class RuntimeDataViewControllerTest {
    @Test void onlyAuthenticatedServiceMayDelegateAUser() {
        String token=UUID.randomUUID().toString();
        var catalog=mock(RuntimeDataViewCatalog.class);
        var controller=new RuntimeDataViewController(new InternalServiceAuthenticator(token),catalog);
        var request=new MockHttpServletRequest();
        request.addHeader(PlatformConstants.HEADER_USER_ID,"reader");
        assertThatThrownBy(() -> controller.list(request))
                .isInstanceOf(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class);
        verifyNoInteractions(catalog);
        request.addHeader(PlatformConstants.HEADER_SERVICE_TOKEN,token);
        when(catalog.list("reader",false)).thenReturn(List.of());
        assertThat(controller.list(request)).isEmpty();
        verify(catalog).list("reader",false);
    }

    @Test void administratorCheckRequiresServiceIdentityAndDoesNotDependOnViews() {
        var catalog=mock(RuntimeDataViewCatalog.class);
        String token=UUID.randomUUID().toString();
        var controller=new RuntimeDataViewController(new InternalServiceAuthenticator(token),catalog);
        var request=new MockHttpServletRequest();
        request.addHeader(PlatformConstants.HEADER_USER_ID,"admin");
        assertThatThrownBy(() -> controller.administrator(request))
                .isInstanceOf(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class);
        verifyNoInteractions(catalog);
        request.addHeader(PlatformConstants.HEADER_SERVICE_TOKEN,token);
        when(catalog.isAdministrator("admin")).thenReturn(true);
        assertThat(controller.administrator(request).getData()).isTrue();
        verify(catalog,never()).list(anyString(),anyBoolean());
    }
}
