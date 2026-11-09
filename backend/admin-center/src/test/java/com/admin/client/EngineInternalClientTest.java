package com.admin.client;

import com.admin.dto.request.ClaimPoolResyncRequest.Pool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EngineInternalClientTest {

    private static final String URL = "http://engine:8080/api/v1/internal/claim-pools/resync";
    private static final String DEPARTED_URL = "http://engine:8080/api/v1/internal/departed-users/tasks/reassign";
    private static final List<Pool> POOLS = List.of(new Pool("bu-1", "E2E_FINANCE", "role-1", "MANAGER"));

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private EngineInternalClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new EngineInternalClient(restTemplate, "http://engine:8080", "svc-token");
    }

    @Test
    void postsPoolsWithServiceTokenOnly() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Service-Token", "svc-token"))
                .andExpect(request -> {
                    if (request.getHeaders().containsKey("X-User-Id")) {
                        throw new AssertionError("resync acts for no user");
                    }
                })
                .andExpect(content().json("{\"pools\":[{\"businessUnitId\":\"bu-1\",\"businessUnitCode\":\"E2E_FINANCE\","
                        + "\"roleId\":\"role-1\",\"roleCode\":\"MANAGER\"}]}", JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"success\":true,\"data\":{\"tasksUpdated\":2}}", MediaType.APPLICATION_JSON));

        client.resyncClaimPools(POOLS);

        server.verify();
    }

    @Test
    void postsDepartedUsersWithServiceTokenOnly() {
        server.expect(requestTo(DEPARTED_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Service-Token", "svc-token"))
                .andExpect(request -> {
                    if (request.getHeaders().containsKey("X-User-Id")) {
                        throw new AssertionError("the reassignment acts for no user");
                    }
                })
                .andExpect(content().json("{\"userIds\":[\"u-1\"]}", JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"success\":true,\"data\":{\"tasksReassigned\":1}}",
                        MediaType.APPLICATION_JSON));

        client.reassignTasksOfDepartedUsers(List.of("u-1"));

        server.verify();
    }

    @Test
    void unacknowledgedAnswerIsAFailure() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"success\":false}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.resyncClaimPools(POOLS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("did not acknowledge");
    }

    @Test
    void engineErrorPropagates() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.resyncClaimPools(POOLS)).isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    void missingServiceTokenFailsAtStartup() {
        assertThatThrownBy(() -> new EngineInternalClient(restTemplate, "http://engine:8080", " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SERVICE_INTERNAL_TOKEN");
    }
}
