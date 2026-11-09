package com.developer.controller;

import com.developer.client.AdminCenterSsoClient;
import com.developer.dto.LoginResponse;
import com.developer.entity.User;
import com.developer.repository.UserRepository;
import com.developer.security.FunctionUnitWorkspaceAccessDeniedException;
import com.developer.service.DeveloperSsoExchangeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** SSO exchange applies the password sign-in rules, and a refused entry leaves no sign-in record (§6.1). */
class AuthSsoExchangeControllerTest {

    private final AdminCenterSsoClient ssoClient = mock(AdminCenterSsoClient.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final DeveloperSsoExchangeService exchangeService = mock(DeveloperSsoExchangeService.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final AuthSsoExchangeController controller =
            new AuthSsoExchangeController(ssoClient, userRepository, exchangeService);

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id("u-1").username("u1").status("ACTIVE").failedLoginCount(2).build();
        when(ssoClient.redeemDeveloperCode("code")).thenReturn(new AdminCenterSsoClient.SsoRedeemResult("u-1"));
        when(userRepository.findById("u-1")).thenReturn(Optional.of(user));
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
    }

    @Test
    void anEntitledUserSignsInAndTheSignInIsRecorded() {
        LoginResponse session = new LoginResponse();
        when(exchangeService.issueSession(user, response)).thenReturn(session);

        ResponseEntity<LoginResponse> result = controller.exchange(body(), request, response);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isSameAs(session);
        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getLastLoginIp()).isEqualTo("10.0.0.1");
        verify(userRepository).save(user);
    }

    @Test
    void aRefusedEntryLeavesNoSignInRecord() {
        when(exchangeService.issueSession(user, response))
                .thenThrow(new FunctionUnitWorkspaceAccessDeniedException("No Developer Workstation access"));

        ResponseEntity<LoginResponse> result = controller.exchange(body(), request, response);

        assertThat(result.getStatusCode().value()).isEqualTo(403);
        assertThat(user.getFailedLoginCount()).isEqualTo(2);
        assertThat(user.getLastLoginAt()).isNull();
        verify(userRepository, never()).save(any());
    }

    @Test
    void anAccountThatIsNotActiveGetsNoSession() {
        user.setStatus("INACTIVE");

        assertThat(controller.exchange(body(), request, response).getStatusCode().value()).isEqualTo(400);
        verify(exchangeService, never()).issueSession(any(), any());
        verify(userRepository, never()).save(any());
    }

    private static AuthSsoExchangeController.SsoExchangeBody body() {
        AuthSsoExchangeController.SsoExchangeBody body = new AuthSsoExchangeController.SsoExchangeBody();
        body.setCode("code");
        return body;
    }
}
