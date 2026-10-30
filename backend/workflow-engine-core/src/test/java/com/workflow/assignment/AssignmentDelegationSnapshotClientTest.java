package com.workflow.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.assignment.AssignmentDelegationSnapshot;
import com.platform.common.assignment.AssignmentFailure;
import com.platform.common.dto.ApiResponse;
import com.platform.common.util.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AssignmentDelegationSnapshotClientTest {
    final RestTemplate http=new RestTemplate();
    final AssignmentDelegationSnapshotClient client=new AssignmentDelegationSnapshotClient(http);
    final MockRestServiceServer server=MockRestServiceServer.bindTo(http).build();
    final AssignmentDelegationSnapshot.Request request=new AssignmentDelegationSnapshot.Request(UUID.randomUUID(),"task","instance","frozen","actual","nominal");
    final ObjectMapper json=JsonUtils.getObjectMapper();
    @BeforeEach void configure() {
        ReflectionTestUtils.setField(client,"baseUrl","http://portal.test");
        ReflectionTestUtils.setField(client,"serviceToken",UUID.randomUUID().toString());
    }
    @Test void directGrantBindsTaskPartiesAndIncludesExpiry() throws Exception {
        var snapshot=new AssignmentDelegationSnapshot(1,request,3,true,4L,Instant.now().plusSeconds(10));
        server.expect(requestTo("http://portal.test/api/portal/internal/assignment/delegation-snapshot"))
                .andExpect(header("X-Internal-Token",(String)ReflectionTestUtils.getField(client,"serviceToken")))
                .andRespond(withSuccess(json.writeValueAsString(ApiResponse.success(snapshot)),MediaType.APPLICATION_JSON));
        assertEquals(snapshot,client.read(request)); server.verify();
    }
    @Test void anotherTaskOrNominalHolderCannotReuseTheGrant() throws Exception {
        var other=new AssignmentDelegationSnapshot.Request(request.caseId(),"other-task",request.instanceId(),request.definitionId(),request.actor(),request.nominalHolder());
        server.expect(anything()).andRespond(withSuccess(json.writeValueAsString(ApiResponse.success(
                new AssignmentDelegationSnapshot(1,other,3,true,4L,Instant.now().plusSeconds(10)))),MediaType.APPLICATION_JSON));
        assertThrows(AssignmentFailure.class,()->client.read(request)); server.verify();
    }
    @Test void unavailablePortalNeverMeansNoStandingRules() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        var failure=assertThrows(AssignmentFailure.class,()->client.read(request));
        assertEquals("ASSIGNMENT_DEPENDENCY_UNAVAILABLE",failure.getMessage()); server.verify();
    }
    @Test void contradictoryRuleOrMissingExpiryFailsClosed() throws Exception {
        server.expect(anything()).andRespond(withSuccess(json.writeValueAsString(ApiResponse.success(
                new AssignmentDelegationSnapshot(1,request,3,true,null,Instant.now().plusSeconds(10)))),MediaType.APPLICATION_JSON));
        assertThrows(AssignmentFailure.class,()->client.read(request)); server.verify();
    }
}
