package com.portal.client;

import com.platform.common.constant.PlatformConstants;
import com.portal.dto.bi.PortalGuestTokenRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class PortalBiClientTest {

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void runtimeAdministratorUsesAuthoritativeDelegatedIdentity(boolean administrator) {
        var transport=new RestTemplate();
        var server=MockRestServiceServer.bindTo(transport).build();
        String token=java.util.UUID.randomUUID().toString();
        var client=new PortalBiClient(transport,"http://admin-center:8090",token);
        server.expect(requestTo("http://admin-center:8090/api/v1/admin/internal/data-views/administrator"))
                .andExpect(header(PlatformConstants.HEADER_SERVICE_TOKEN,token))
                .andExpect(header(PlatformConstants.HEADER_USER_ID,"reader"))
                .andRespond(withSuccess("{\"success\":true,\"data\":"+administrator+"}",MediaType.APPLICATION_JSON));
        assertThat(client.isDataViewAdministrator("reader")).isEqualTo(administrator);
        server.verify();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"{}", "{\"success\":false,\"data\":true}"})
    void missingOrUnsuccessfulAdministratorResponseNeverGrantsAccess(String response) {
        var transport=new RestTemplate();
        var server=MockRestServiceServer.bindTo(transport).build();
        var client=new PortalBiClient(transport,"http://admin-center:8090",java.util.UUID.randomUUID().toString());
        server.expect(requestTo("http://admin-center:8090/api/v1/admin/internal/data-views/administrator"))
                .andRespond(withSuccess(response,MediaType.APPLICATION_JSON));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.isDataViewAdministrator("reader"))
                .isInstanceOf(IllegalStateException.class);
        server.verify();
    }

    @Test
    void guestTokenCallUsesOnlyServiceIdentityAndDelegatedPortalUser() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        PortalBiClient client = new PortalBiClient(
                restTemplate, "http://admin-center:8090", "service-secret");

        server.expect(requestTo("http://admin-center:8090/api/v1/admin/internal/bi/guest-token"))
                .andExpect(method(POST))
                .andExpect(header(PlatformConstants.HEADER_SERVICE_TOKEN, "service-secret"))
                .andExpect(header(PlatformConstants.HEADER_USER_ID, "portal-user"))
                .andExpect(request -> assertThat(request.getHeaders().containsKey(HttpHeaders.COOKIE)).isFalse())
                .andExpect(content().json("""
                        {"dashboardId":"dashboard-1","dataViewId":null,"activeBusinessUnitId":"bu-finance"}
                        """))
                .andRespond(withSuccess("""
                        {"token":"guest-token","dashboardEmbedId":"embed-1"}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.getGuestToken(
                "portal-user",
                new PortalGuestTokenRequest("dashboard-1", null, "bu-finance"))).isNotNull();
        server.verify();
    }
    @Test
    void viewDirectoryUsesServiceAuthenticationAndNeverFallsBackToDesigner() {
        RestTemplate transport=new RestTemplate();
        var server=MockRestServiceServer.bindTo(transport).build();
        String token=java.util.UUID.randomUUID().toString();
        var client=new PortalBiClient(transport,"http://admin-center:8090",token);
        server.expect(requestTo("http://admin-center:8090/api/v1/admin/internal/data-views"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header(PlatformConstants.HEADER_SERVICE_TOKEN,token))
                .andExpect(header(PlatformConstants.HEADER_USER_ID,"reader"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withServerError());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.getDataViews("reader"))
                .isInstanceOf(com.portal.exception.PortalBiUpstreamException.class);
        server.verify();
    }
}
