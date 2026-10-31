package com.workflow.assignment;

import com.platform.common.assignment.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AssignmentIdentityClientTest {
    final RestTemplate http=new RestTemplate();
    final AssignmentIdentityClient client=new AssignmentIdentityClient(http);
    final MockRestServiceServer server=MockRestServiceServer.bindTo(http).build();
    final List<AssignmentIdentitySnapshot.Reference> references=List.of(new AssignmentIdentitySnapshot.Reference("id",AssignmentIdentitySnapshot.Kind.ID));
    @BeforeEach void configure() {
        ReflectionTestUtils.setField(client,"baseUrl","http://authority.test");
        ReflectionTestUtils.setField(client,"serviceToken",java.util.UUID.randomUUID().toString());
    }
    @Test void missingReferenceIsAnExplicitAuthorityResult() {
        server.expect(requestTo("http://authority.test/api/v1/admin/internal/assignment-identities"))
                .andRespond(withSuccess(withWindow("{\"success\":true,\"data\":{\"authorityRevision\":3,\"users\":{},\"missingReferences\":[\"id\"]}}"),MediaType.APPLICATION_JSON));
        var snapshot=client.resolve(references); assertTrue(snapshot.users().isEmpty()); assertTrue(snapshot.missingReferences().contains("id")); server.verify();
    }
    @Test void wrongCanonicalIdIsRejectedInsteadOfGrantingAnotherAccount() {
        server.expect(anything()).andRespond(withSuccess(withWindow("{\"success\":true,\"data\":{\"authorityRevision\":3,\"users\":{\"id\":{\"id\":\"someone-else\",\"username\":\"alice\",\"active\":true}},\"missingReferences\":[]}}"),MediaType.APPLICATION_JSON));
        assertThrows(AssignmentFailure.class,()->client.resolve(references)); server.verify();
    }
    @Test void transportFailureNeverTurnsIntoAnEmptyPool() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        var failure=assertThrows(AssignmentFailure.class,()->client.resolve(references));
        assertEquals("ASSIGNMENT_DEPENDENCY_UNAVAILABLE",failure.getMessage()); server.verify();
    }
    @Test void contradictoryMissingAndPresentReferenceIsRejected() {
        server.expect(anything()).andRespond(withSuccess(withWindow("{\"success\":true,\"data\":{\"authorityRevision\":3,\"users\":{\"id\":{\"id\":\"id\",\"username\":\"alice\",\"active\":true}},\"missingReferences\":[\"id\"]}}"),MediaType.APPLICATION_JSON));
        assertThrows(AssignmentFailure.class,()->client.resolve(references)); server.verify();
    }
    @Test void anAuthorityWithoutTheTemporalContractCannotAuthorizeProtectedWork() {
        server.expect(anything()).andRespond(withSuccess("{\"success\":true,\"data\":{\"authorityRevision\":3,\"users\":{},\"missingReferences\":[\"id\"]}}",MediaType.APPLICATION_JSON));
        assertThrows(AssignmentFailure.class,()->client.resolve(references)); server.verify();
    }
    private String withWindow(String body) {
        try {
            var parsed=com.platform.common.util.JsonUtils.getObjectMapper().readTree(body);
            ((com.fasterxml.jackson.databind.node.ObjectNode)parsed.get("data")).putObject("timeWindow")
                    .put("observedAt","2026-10-27T00:00:00Z").putNull("previousBoundary").putNull("nextBoundary");
            return parsed.toString();
        } catch (java.io.IOException invalidFixture) { throw new IllegalArgumentException(invalidFixture); }
    }
}
