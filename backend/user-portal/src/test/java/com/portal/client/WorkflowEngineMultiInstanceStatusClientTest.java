package com.portal.client;

import com.platform.common.functionunit.FunctionUnitVersionException;
import com.platform.common.i18n.I18nService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/** MI status keyed by the request's release: a release the engine cannot use reaches the page as a version error. */
class WorkflowEngineMultiInstanceStatusClientTest {
    private static final String URL = "http://synthetic-engine/api/v1/workflow/multi-instance/pi-1/status";

    private final WorkflowEngineClient engine = mock(WorkflowEngineClient.class);
    private final RestTemplate http = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.createServer(http);
    private final WorkflowEngineProcessClient client = new WorkflowEngineProcessClient(engine, mock(I18nService.class));

    @BeforeEach
    void setUp() {
        when(engine.isAvailable()).thenReturn(true);
        when(engine.engineUrl()).thenReturn("http://synthetic-engine");
        when(engine.restTemplate()).thenReturn(http);
    }

    @Test
    void engineVersionErrorIsSurfacedNotRenderedAsNoProgress() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                .body("{\"success\":false,\"error\":{\"code\":\"3008\",\"message\":\"VERSION_SNAPSHOT_INCOMPLETE\","
                        + "\"details\":{\"reason\":\"VERSION_SNAPSHOT_INCOMPLETE\",\"reference\":\"rel-1\"}}}"));

        assertThatThrownBy(() -> client.getMultiInstanceStatus("pi-1"))
                .isInstanceOf(FunctionUnitVersionException.class)
                .satisfies(e -> assertThat(((FunctionUnitVersionException) e).getDetails())
                        .containsEntry("reason", "VERSION_SNAPSHOT_INCOMPLETE").containsEntry("reference", "rel-1"));
        server.verify();
    }

    @Test
    void otherConflictsKeepTheirExistingHandling() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                .body("{\"success\":false,\"error\":{\"code\":\"OPTIMISTIC_LOCK\",\"message\":\"busy\"}}"));

        assertThat(client.getMultiInstanceStatus("pi-1")).isEmpty();
        server.verify();
    }
}
