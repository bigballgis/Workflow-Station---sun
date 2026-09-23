package com.admin.config;

import com.platform.common.constant.PlatformConstants;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalServiceAuthenticatorTest {

    private final InternalServiceAuthenticator authenticator =
            new InternalServiceAuthenticator("service-secret");

    @Test
    void acceptsAValidServiceTokenAndDelegatedUser() {
        MockHttpServletRequest request = request("service-secret", "portal-user");

        assertThat(authenticator.requireDelegatedUserId(request)).isEqualTo("portal-user");
    }

    @Test
    void rejectsMissingOrIncorrectServiceToken() {
        assertThatThrownBy(() -> authenticator.requireDelegatedUserId(request(null, "portal-user")))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> authenticator.requireDelegatedUserId(request("wrong", "portal-user")))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void rejectsMissingOrSystemDelegatedIdentity() {
        assertThatThrownBy(() -> authenticator.requireDelegatedUserId(request("service-secret", null)))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> authenticator.requireDelegatedUserId(request("service-secret", "system")))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    private static MockHttpServletRequest request(String serviceToken, String userId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (serviceToken != null) {
            request.addHeader(PlatformConstants.HEADER_SERVICE_TOKEN, serviceToken);
        }
        if (userId != null) {
            request.addHeader(PlatformConstants.HEADER_USER_ID, userId);
        }
        return request;
    }
}
