package com.portal.client;

import com.platform.common.assignment.*;
import com.platform.common.util.JsonUtils;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AssignmentTaskWriteReadClientTest {
    final RestTemplate http=new RestTemplate();
    final WorkflowEngineClient engine=mock(WorkflowEngineClient.class);
    final MockRestServiceServer server=MockRestServiceServer.bindTo(http).build();
    final AssignmentTaskWriteReadClient client=new AssignmentTaskWriteReadClient(engine,http);
    @BeforeEach void setup() {
        when(engine.engineUrl()).thenReturn("http://127.0.0.1:18179");
        var headers=new HttpHeaders(); headers.setBearerAuth("synthetic-test-token");
        when(engine.authorizedGetEntity()).thenReturn(new HttpEntity<>(headers));
    }
    @Test void typedSnapshotIsBoundToTheTaskAndActorAndDoesNotTreatEngineRoleResultAsAPortalGrant() {
        respond("task","actor",snapshot(true,"CURRENT",Instant.now().plusSeconds(30)));
        assertTrue(client.read("task","actor").exclusionAllowsWrite());
        server.verify();
    }
    @Test void anotherTaskOrActorCannotBeReused() {
        respond("another-task","actor",snapshot(true,"CURRENT",Instant.now().plusSeconds(30)));
        assertThrows(AssignmentFailure.class,()->client.read("task","actor")); server.verify();
        server.reset();
        respond("task","another-actor",snapshot(true,"CURRENT",Instant.now().plusSeconds(30)));
        assertThrows(AssignmentFailure.class,()->client.read("task","actor")); server.verify();
    }
    @Test void expiredOrUnknownPositiveDecisionsNeverAuthorize() {
        respond("task","actor",snapshot(true,"CURRENT",Instant.now().minusSeconds(1)));
        assertThrows(AssignmentFailure.class,()->client.read("task","actor")); server.verify();
        server.reset(); respond("task","actor",snapshot(true,"FUTURE_UNKNOWN",Instant.now().plusSeconds(30)));
        assertThrows(AssignmentFailure.class,()->client.read("task","actor")); server.verify();
    }
    @Test void oldEndpointWithoutTypedMetadataCannotBeAssumedToSupportProtection() {
        respond("task","actor",Map.of());
        assertThrows(AssignmentFailure.class,()->client.read("task","actor")); server.verify();
    }
    @Test void dependencyDenialRemainsReadOnlyWithoutInventingLegacy() {
        respond("task","actor",new AssignmentTaskWriteReadSnapshot(1,null,false,"ASSIGNMENT_DEPENDENCY_UNAVAILABLE",null));
        var result=client.read("task","actor"); assertFalse(result.exclusionAllowsWrite()); assertNull(result.tracked());
        server.verify();
    }
    private AssignmentTaskWriteReadSnapshot snapshot(boolean allowed,String reason,Instant deadline) {
        return new AssignmentTaskWriteReadSnapshot(1,true,allowed,reason,new AssignmentTaskReadSnapshot.Stamp(2L,3,deadline));
    }
    private void respond(String task,String actor,Object access) {
        var data=Map.of("taskId",task,"userId",actor,"hasPermission",false,"assignmentAccess",access);
        server.expect(requestTo("http://127.0.0.1:18179/api/v1/tasks/task/check-permission?userId=actor"))
                .andExpect(method(HttpMethod.GET)).andExpect(header(HttpHeaders.AUTHORIZATION,"Bearer synthetic-test-token"))
                .andRespond(withSuccess(JsonUtils.toJson(Map.of("success",true,"code","SUCCESS","data",data)),MediaType.APPLICATION_JSON));
    }
}
