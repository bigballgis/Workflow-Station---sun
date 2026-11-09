package com.platform.security.client;

import com.platform.common.constant.PlatformConstants;
import com.platform.common.dto.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** Outbound calls to Admin Center carry the service token and the acting user; nothing else is touched. */
class ServiceTokenRequestInterceptorTest {

    private final ServiceTokenRequestInterceptor interceptor =
            new ServiceTokenRequestInterceptor("http://admin-center:8080", "svc-secret");
    private final ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminCallCarriesServiceTokenAndSignedInUser() throws Exception {
        signIn("u-1");
        MockClientHttpRequest request = request("http://admin-center:8080/api/v1/admin/users/u-2");

        interceptor.intercept(request, new byte[0], execution);

        assertThat(request.getHeaders().getFirst(PlatformConstants.HEADER_SERVICE_TOKEN)).isEqualTo("svc-secret");
        assertThat(request.getHeaders().getFirst(PlatformConstants.HEADER_USER_ID)).isEqualTo("u-1");
    }

    @Test
    void backgroundCallWithoutUserCarriesOnlyTheToken() throws Exception {
        MockClientHttpRequest request = request("http://admin-center:8080/api/v1/admin/task-assignment/x");

        interceptor.intercept(request, new byte[0], execution);

        assertThat(request.getHeaders().getFirst(PlatformConstants.HEADER_SERVICE_TOKEN)).isEqualTo("svc-secret");
        assertThat(request.getHeaders().containsKey(PlatformConstants.HEADER_USER_ID)).isFalse();
    }

    @Test
    void headersSetByTheCallSiteAreKept() throws Exception {
        signIn("u-1");
        MockClientHttpRequest request = request("http://admin-center:8080/api/v1/admin/roles/r/members/u-9");
        request.getHeaders().set(PlatformConstants.HEADER_USER_ID, "approver-7");

        interceptor.intercept(request, new byte[0], execution);

        assertThat(request.getHeaders().getFirst(PlatformConstants.HEADER_USER_ID)).isEqualTo("approver-7");
    }

    @Test
    void otherTargetsAreLeftUntouched() throws Exception {
        signIn("u-1");
        for (String url : List.of("http://workflow-engine:8080/api/v1/tasks", "http://admin-center:9999/api/v1/admin",
                "https://admin-center:8080/api/v1/admin", "http://ai-gateway/v1/chat")) {
            MockClientHttpRequest request = request(url);

            interceptor.intercept(request, new byte[0], execution);

            assertThat(request.getHeaders().containsKey(PlatformConstants.HEADER_SERVICE_TOKEN)).as(url).isFalse();
        }
    }

    @Test
    void missingTokenStopsTheServiceAtStartup() {
        assertThatThrownBy(() -> new ServiceTokenRequestInterceptor("http://admin-center:8080", " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SERVICE_INTERNAL_TOKEN");
    }

    private static MockClientHttpRequest request(String url) {
        return new MockClientHttpRequest(HttpMethod.GET, URI.create(url));
    }

    private static void signIn(String userId) {
        UserPrincipal principal = UserPrincipal.builder().userId(userId).username(userId).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
}
