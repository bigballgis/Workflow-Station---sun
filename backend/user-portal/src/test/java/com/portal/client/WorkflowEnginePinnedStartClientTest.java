package com.portal.client;

import com.platform.common.i18n.I18nService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class WorkflowEnginePinnedStartClientTest {
    private final WorkflowEngineClient engine = mock(WorkflowEngineClient.class);
    private final RestTemplate http = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.createServer(http);
    private final WorkflowEngineProcessClient client = new WorkflowEngineProcessClient(engine, mock(I18nService.class));
    private static final String URL = "http://synthetic-engine/api/v1/processes/instances";
    private void prepare() {
        when(engine.isAvailable()).thenReturn(true);
        when(engine.engineUrl()).thenReturn("http://synthetic-engine");
        when(engine.restTemplate()).thenReturn(http);
        var starts=mock(AssignmentStartCoordinationClient.class);
        when(starts.prepare(eq("synthetic"),nullable(String.class))).thenAnswer(call->new com.platform.common.assignment.AssignmentStartContext(
                1,"synthetic:1:verified","synthetic",false,call.getArgument(1)));
        org.springframework.test.util.ReflectionTestUtils.setField(client,"assignmentStarts",starts);
    }
    @Test void pinnedStartUsesTheDistinctRouteAndAnExplicitTypedField() {
        prepare();
        when(engine.hasInboundAuthorization()).thenReturn(true);
        server.expect(requestTo(URL + "/pinned"))
                .andExpect(header(com.platform.common.assignment.AssignmentStartContext.HEADER,"synthetic:1:verified"))
                .andExpect(jsonPath("$.expectedProcessDefinitionId").value("synthetic:1:verified"))
                .andExpect(jsonPath("$.variables.synthetic").value(true))
                .andExpect(jsonPath("$.variables.__subTableBatchRuntime__").doesNotExist())
                .andRespond(withSuccess("{\"success\":true,\"data\":{\"processInstanceId\":\"synthetic-instance\"}}", MediaType.APPLICATION_JSON));
        assertThat(client.startProcess("synthetic", null, "synthetic-user", Map.of("synthetic", true), "synthetic:1:verified"))
                .containsEntry("processInstanceId", "synthetic-instance");
        server.verify();
    }
    @Test void anOldEngineRejectingTheRouteCannotBeRetriedAsAnUnpinnedStart() {
        prepare();
        when(engine.hasInboundAuthorization()).thenReturn(true);
        server.expect(requestTo(URL + "/pinned")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> client.startProcess("synthetic", null, null, Map.of(), "synthetic:1:verified"))
                .hasMessageContaining("404");
        server.verify();
    }
    @Test void emailStartWithoutJwtPinsOnThePermitAllRoute() {
        prepare();
        when(engine.hasInboundAuthorization()).thenReturn(false);
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.expectedProcessDefinitionId").value("synthetic:1:verified"))
                .andRespond(withSuccess("{\"success\":true,\"data\":{\"processInstanceId\":\"email-instance\"}}", MediaType.APPLICATION_JSON));
        assertThat(client.startProcess("synthetic", null, "synthetic-user", Map.of(), "synthetic:1:verified"))
                .containsEntry("processInstanceId", "email-instance");
        server.verify();
    }
    @Test void legacyStartKeepsTheOriginalRouteAndRequestShape() {
        prepare();
        server.expect(requestTo(URL)).andExpect(jsonPath("$.expectedProcessDefinitionId").doesNotExist())
                .andRespond(withSuccess("{\"success\":true,\"data\":{\"processInstanceId\":\"legacy-instance\"}}", MediaType.APPLICATION_JSON));
        assertThat(client.startProcess("synthetic", null, null, Map.of())).containsEntry("processInstanceId", "legacy-instance");
        server.verify();
    }
    @Test void aRejectedPreflightNeverCallsTheOrdinaryStartEndpoint() {
        prepare();
        var starts=(AssignmentStartCoordinationClient)org.springframework.test.util.ReflectionTestUtils.getField(client,"assignmentStarts");
        when(starts.prepare(anyString(),nullable(String.class))).thenThrow(new com.platform.common.assignment.AssignmentFailure(
                com.platform.common.assignment.AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE));
        assertThatThrownBy(()->client.startProcess("synthetic",null,null,Map.of())).isInstanceOf(com.platform.common.assignment.AssignmentFailure.class);
        server.verify();
    }
}
