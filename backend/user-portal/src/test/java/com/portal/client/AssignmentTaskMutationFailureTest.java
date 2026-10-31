package com.portal.client;

import com.platform.common.assignment.AssignmentFailure;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AssignmentTaskMutationFailureTest {
    @ParameterizedTest @ValueSource(strings={"claim","unclaim","delegate","transfer","reassign-claim","return","assign-subtable-row"})
    void protectedDenialsRetainTheirReasonAcrossExistingMutationClients(String operation) {
        var http=new RestTemplate(); var server=MockRestServiceServer.bindTo(http).build();
        var engine=mock(WorkflowEngineClient.class);
        when(engine.isAvailable()).thenReturn(true); when(engine.engineUrl()).thenReturn("http://engine.test");
        when(engine.restTemplate()).thenReturn(http);
        String path="assign-subtable-row".equals(operation)?"sub-table-rows/1/assign":operation;
        server.expect(requestTo("http://engine.test/api/v1/tasks/task/"+path)).andRespond(withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON).body("{\"success\":false,\"error\":{\"details\":{\"reason\":\"ASSIGNMENT_EXCLUDED\"}}}"));
        var client=new WorkflowEngineTaskClient(engine);
        var failure=assertThrows(AssignmentFailure.class,()->{
            switch(operation) {
                case "claim" -> client.claimTask("task","actor");
                case "unclaim" -> client.unclaimTask("task","actor");
                case "delegate" -> client.delegateTask("task",Map.of("delegatedTo","target"));
                case "transfer" -> client.transferTask("task","actor","target","reason");
                case "reassign-claim" -> client.reassignClaim("task","actor","target");
                case "return" -> client.returnTask("task","start","actor","reason");
                case "assign-subtable-row" -> client.assignSubTableRow("task",1L,"actor");
                default -> throw new AssertionError(operation);
            }
        });
        assertEquals("ASSIGNMENT_EXCLUDED",failure.getDetails().get("reason")); server.verify();
    }
    @ParameterizedTest @ValueSource(strings={"", "not-json", "null", "{\"error\":{\"message\":\"Legacy denial\"}}", "{\"error\":{\"details\":{\"reason\":\"LEGACY_REASON\"}}}"})
    void untypedLegacyResponsesKeepTheirExistingCallerHandling(String body) {
        var response=org.springframework.web.client.HttpClientErrorException.create(HttpStatus.BAD_REQUEST,"legacy",null,
                body.getBytes(java.nio.charset.StandardCharsets.UTF_8),java.nio.charset.StandardCharsets.UTF_8);
        assertDoesNotThrow(()->AssignmentRemoteFailure.rethrowKnown(response));
    }
}
