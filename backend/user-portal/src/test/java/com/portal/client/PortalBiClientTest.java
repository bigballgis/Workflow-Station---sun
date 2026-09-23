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
}
