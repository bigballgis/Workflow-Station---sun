package com.admin.service;

import com.admin.config.PlatformSsoProperties;
import com.admin.repository.LoginAuditQueryRepository;
import com.admin.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformSsoServiceRedirectTest {

    private static final String VSCODE_CALLBACK = "vscode://workflow-station.dw-copilot/sso-callback";

    /**
     * Reproduces UAT: SPRING_APPLICATION_JSON (higher precedence) redefines redirect-uri-prefixes, which replaces the
     * whole list from application.yml. The fixed list is not in the override, so it must survive the merge.
     */
    private static PlatformSsoProperties bindLikeUat() {
        var environmentOverride = new MapConfigurationPropertySource(Map.of(
                "platform.sso.clients.developer-workstation.redirect-uri-prefixes[0]", "https://dw.example/dev/sso/callback"));
        var applicationYml = new MapConfigurationPropertySource(Map.of(
                "platform.sso.clients.developer-workstation.redirect-uri-prefixes[0]", "http://localhost:3000/dev/sso/callback",
                "platform.sso.clients.developer-workstation.redirect-uri-prefixes[1]", "vscode://placed-in-the-wrong-list/",
                "platform.sso.clients.developer-workstation.fixed-redirect-uri-prefixes[0]", "vscode://workflow-station.dw-copilot/",
                "platform.sso.clients.developer-workstation.loopback-redirect-paths[0]", "/sso-callback"));
        return new Binder(environmentOverride, applicationYml).bind("platform.sso", PlatformSsoProperties.class).get();
    }

    @SuppressWarnings("unchecked")
    private static PlatformSsoService service(PlatformSsoProperties properties) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(mock(ValueOperations.class));
        return new PlatformSsoService(properties, mock(UserRepository.class), mock(PasswordEncoder.class), redis,
                new ObjectMapper(), mock(LoginAuditQueryRepository.class), mock(ObjectProvider.class));
    }

    @Test
    void anEnvironmentOverrideReplacesTheRedirectListButKeepsTheFixedOne() {
        var client = bindLikeUat().getClients().get("developer-workstation");

        assertThat(client.getRedirectUriPrefixes()).containsExactly("https://dw.example/dev/sso/callback");
        assertThat(client.getFixedRedirectUriPrefixes()).containsExactly("vscode://workflow-station.dw-copilot/");
        assertThat(client.getLoopbackRedirectPaths()).containsExactly("/sso-callback");
    }

    @Test
    void theExtensionCallbackIsAcceptedUnderAnEnvironmentOverride() {
        var response = service(bindLikeUat()).issueCodeForUser("u1", "developer-workstation", VSCODE_CALLBACK, "s1");

        assertThat(response.getRedirectUri()).isEqualTo(VSCODE_CALLBACK);
        assertThat(response.getAuthorizationCode()).isNotBlank();
    }

    @Test
    void theOverriddenEnvironmentPrefixIsStillAccepted() {
        var response = service(bindLikeUat()).issueCodeForUser(
                "u1", "developer-workstation", "https://dw.example/dev/sso/callback?x=1", null);

        assertThat(response.getAuthorizationCode()).isNotBlank();
    }

    @Test
    void anUnlistedCallbackIsStillRejected() {
        var service = service(bindLikeUat());

        assertThatThrownBy(() -> service.issueCodeForUser("u1", "developer-workstation", "vscode://evil.extension/cb", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("redirectUri not allowed");
        assertThatThrownBy(() -> service.issueCodeForUser("u1", "developer-workstation", "vscode://placed-in-the-wrong-list/", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theFixedListBelongsToItsOwnClientOnly() {
        var properties = bindLikeUat();
        var portal = new PlatformSsoProperties.SsoClient();
        portal.setRedirectUriPrefixes(List.of("https://dw.example/portal/sso/callback"));
        properties.getClients().put("portal", portal);

        assertThatThrownBy(() -> service(properties).issueCodeForUser("u1", "portal", VSCODE_CALLBACK, null))
                .hasMessage("redirectUri not allowed");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1:54321/sso-callback",
            "http://127.0.0.1:1/sso-callback",
            "http://127.0.0.1:65535/sso-callback"})
    void theExtensionLoopbackCallbackIsAcceptedOnAnyPortUnderAnEnvironmentOverride(String redirectUri) {
        var response = service(bindLikeUat()).issueCodeForUser("u1", "developer-workstation", redirectUri, "s1");

        assertThat(response.getRedirectUri()).isEqualTo(redirectUri);
    }

    /** The login page does {@code new URL(redirectUri)} and navigates there with the code appended. */
    @ParameterizedTest
    @ValueSource(strings = {
            // userinfo: a "http://127.0.0.1:" prefix would accept these, and the browser sends the code to evil.com
            "http://127.0.0.1:80@evil.com/sso-callback",
            "http://127.0.0.1:80@evil.com/",
            "http://127.0.0.1:80\\@evil.com/sso-callback",
            "http://127.0.0.1.evil.com:80/sso-callback",
            "http://127.0.0.1/sso-callback",
            "http://127.0.0.1:/sso-callback",
            "http://127.0.0.1:0/sso-callback",
            "http://127.0.0.1:065/sso-callback",
            "http://127.0.0.1:65536/sso-callback",
            "http://localhost:54321/sso-callback",
            "http://[::1]:54321/sso-callback",
            "https://127.0.0.1:54321/sso-callback",
            "HTTP://127.0.0.1:54321/sso-callback",
            "http://127.0.0.1:54321/sso-callbackx",
            "http://127.0.0.1:54321/sso-callback/../other",
            "http://127.0.0.1:54321/sso-callback?next=https://evil.com",
            "http://127.0.0.1:54321/sso-callback#x",
            "http://127.0.0.1:54321/other"})
    void anythingButTheExactLoopbackCallbackIsRejected(String redirectUri) {
        var service = service(bindLikeUat());

        assertThatThrownBy(() -> service.issueCodeForUser("u1", "developer-workstation", redirectUri, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("redirectUri not allowed");
    }

    @Test
    void aLoopbackPrefixWouldHaveSentTheCodeToAnotherHost() {
        String bypass = "http://127.0.0.1:80@evil.com/sso-callback";

        assertThat(bypass).startsWith("http://127.0.0.1:");
        assertThat(URI.create(bypass).getHost()).isEqualTo("evil.com");
    }

    @Test
    void theLoopbackCallbackBelongsToItsOwnClientOnly() {
        var properties = bindLikeUat();
        var portal = new PlatformSsoProperties.SsoClient();
        portal.setRedirectUriPrefixes(List.of("https://dw.example/portal/sso/callback"));
        properties.getClients().put("portal", portal);

        assertThatThrownBy(() -> service(properties).issueCodeForUser("u1", "portal", "http://127.0.0.1:54321/sso-callback", null))
                .hasMessage("redirectUri not allowed");
    }
}
