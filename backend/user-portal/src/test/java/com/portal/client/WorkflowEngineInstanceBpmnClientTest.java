package com.portal.client;

import com.platform.common.i18n.I18nService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class WorkflowEngineInstanceBpmnClientTest {
    private final WorkflowEngineClient engine = mock(WorkflowEngineClient.class);
    private final RestTemplate http = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.createServer(http);
    private final WorkflowEngineProcessClient client = new WorkflowEngineProcessClient(engine, mock(I18nService.class));
    private static final String URL = "http://synthetic-engine/api/v1/processes/definitions/process/bpmn";

    @BeforeEach
    void setUp() {
        when(engine.isAvailable()).thenReturn(true);
        when(engine.engineUrl()).thenReturn("http://synthetic-engine");
        when(engine.restTemplate()).thenReturn(http);
    }

    @Test
    void forwardsInstanceQueryAndRequiresMatchingSourceIdentity() {
        server.expect(requestTo(URL + "?processInstanceId=old-instance")).andRespond(withSuccess("""
                {"data":{"processInstanceId":"old-instance","processDefinitionKey":"process",
                "processDefinitionId":"process:1:old","bpmnXml":"<old/>"}}
                """, MediaType.APPLICATION_JSON));
        assertThat(client.getBpmnXml("process", "old-instance")).contains("<old/>");
        server.verify();
    }

    @Test
    void anOldEngineIgnoringTheQueryCannotSilentlyReturnLatestBpmn() {
        server.expect(requestTo(URL + "?processInstanceId=old-instance")).andRespond(withSuccess(
                "{\"data\":{\"processDefinitionKey\":\"process\",\"bpmnXml\":\"<new/>\"}}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.getBpmnXml("process", "old-instance")).hasMessageContaining("cannot be read");
        server.verify();
    }

    @Test
    void failureIsNotRetriedAsAnUnpinnedRequest() {
        server.expect(requestTo(URL + "?processInstanceId=old-instance")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertThatThrownBy(() -> client.getBpmnXml("process", "old-instance")).hasMessageContaining("cannot be read");
        server.verify();
    }

    @Test
    void malformedSuccessAndForeignInstanceAreRejected() {
        server.expect(requestTo(URL + "?processInstanceId=old-instance")).andRespond(withSuccess("""
                {"data":{"processInstanceId":"other-instance","processDefinitionKey":"process",
                "processDefinitionId":"process:2:new","bpmnXml":"<new/>"}}
                """, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.getBpmnXml("process", "old-instance")).hasMessageContaining("cannot be read");
        server.verify();
    }

    @Test
    void unavailableEngineAndMissingInstanceDoNotYieldAnEmptySuccess() {
        when(engine.isAvailable()).thenReturn(false);
        assertThatThrownBy(() -> client.getBpmnXml("process", "old-instance")).hasMessageContaining("unavailable");
        assertThatThrownBy(() -> client.getBpmnXml("process", " ")).hasMessageContaining("required");
        server.verify();
    }

    @Test
    void legacyRequestStillReadsItsOriginalResponseShape() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"data\":{\"bpmnXml\":\"<latest/>\"}}", MediaType.APPLICATION_JSON));
        assertThat(client.getBpmnXml("process")).contains("<latest/>");
        server.verify();
    }

    @Test
    void emptyXmlWithOtherwiseCorrectIdentityIsNotAccepted() {
        server.expect(requestTo(URL + "?processInstanceId=old-instance")).andRespond(withSuccess("""
                {"data":{"processInstanceId":"old-instance","processDefinitionKey":"process",
                "processDefinitionId":"process:1:old","bpmnXml":""}}
                """, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.getBpmnXml("process", "old-instance")).hasMessageContaining("response is invalid");
        server.verify();
    }
}
