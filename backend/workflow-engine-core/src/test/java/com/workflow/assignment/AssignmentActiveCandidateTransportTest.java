package com.workflow.assignment;

import com.workflow.client.AdminCenterClient;
import com.platform.common.assignment.AssignmentFailure;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AssignmentActiveCandidateTransportTest {
    RestTemplate http=new RestTemplate();
    MockRestServiceServer server=MockRestServiceServer.bindTo(http).build();
    AdminCenterClient client=new AdminCenterClient(http);
    @BeforeEach void setUp() {
        ReflectionTestUtils.setField(client,"adminCenterUrl","http://admin.test");
        ReflectionTestUtils.setField(client,"serviceInternalToken","synthetic-internal-token");
    }
    @AfterEach void verify() { server.verify(); assertFalse(AssignmentIdentityReadScope.active()); }
    @Test void protectedSourcesUseAuthenticatedPrivateQueriesAndAcceptAnEmptyValidPool() {
        for(String kind:List.of("BU_ROLE","ROLE","VIRTUAL_GROUP")) {
            server.expect(requestTo("http://admin.test/api/v1/admin/internal/assignment-identities/candidates"))
                    .andExpect(header("X-Internal-Token","synthetic-internal-token"))
                    .andExpect(jsonPath("$.kind").value(kind))
                    .andRespond(withSuccess("{\"success\":true,\"data\":[]}",MediaType.APPLICATION_JSON));
        }
        assertTrue(AssignmentIdentityReadScope.fresh(()->client.getUsersByBusinessUnitAndRole("BU","ROLE")).isEmpty());
        assertTrue(AssignmentIdentityReadScope.fresh(()->client.getUsersByUnboundedRole("ROLE")).isEmpty());
        assertTrue(AssignmentIdentityReadScope.fresh(()->client.getUsersByVirtualGroupCode("GROUP")).isEmpty());
    }
    @Test void legacySourceKeepsItsExistingPublicContract() {
        server.expect(requestTo("http://admin.test/api/v1/admin/task-assignment/roles/ROLE/users"))
                .andRespond(withSuccess("[\"u\"]",MediaType.APPLICATION_JSON));
        assertEquals(List.of("u"),client.getUsersByUnboundedRole("ROLE"));
    }
    @Test void absentPrivateAuthorityDoesNotFallBackToLegacyOrEmptyPool() {
        server.expect(requestTo("http://admin.test/api/v1/admin/internal/assignment-identities/candidates"))
                .andRespond(withResourceNotFound());
        assertThrows(AssignmentFailure.class,()->AssignmentIdentityReadScope.fresh(()->client.getUsersByUnboundedRole("ROLE")));
    }
    @Test void sourceResolverPreservesDependencyFailureClassification() {
        var admin=org.mockito.Mockito.spy(client);
        org.mockito.Mockito.doReturn(true).when(admin).isEligibleRole("BU","ROLE");
        server.expect(requestTo("http://admin.test/api/v1/admin/internal/assignment-identities/candidates"))
                .andRespond(withServerError());
        var resolver=new com.workflow.service.TaskAssigneeResolver(admin);
        var failure=assertThrows(AssignmentFailure.class,()->AssignmentIdentityReadScope.fresh(
                ()->resolver.resolveWithRoleIds("BU_ROLE",List.of("ROLE"),"BU","initiator",null,null)));
        assertEquals("ASSIGNMENT_DEPENDENCY_UNAVAILABLE",failure.getDetails().get("reason"));
    }
    @Test void singleTaskDelegationUsesPrivatePoolAndCannotGrantFromInactiveOrganization() {
        server.expect(requestTo("http://admin.test/api/v1/admin/internal/assignment-identities/candidates"))
                .andRespond(withSuccess("{\"success\":true,\"data\":[]}",MediaType.APPLICATION_JSON));
        var standing=org.mockito.Mockito.mock(AssignmentDelegationSnapshotClient.class);
        var until=java.time.Instant.now().plusSeconds(5);
        var task=org.mockito.Mockito.mock(org.flowable.task.api.Task.class);
        var command=new AssignmentWriteGuard.Command("task",AssignmentMutationScope.Operation.COMPLETE,"actor",null,null);
        var holding=new AssignmentWriteGuard.Holding("holder",null,"BU_ROLE","BU","ROLE");
        var qualification=new AssignmentQualificationComponent.Prepared(7,until,List.of("holder"),List.of(),false,java.util.Map.of());
        var decision=new AssignmentCaseEvaluator.Decision("task",java.util.UUID.randomUUID(),1,null,java.util.Set.of(),qualification,null,0);
        org.mockito.Mockito.when(standing.read(org.mockito.ArgumentMatchers.any())).thenReturn(
                new com.platform.common.assignment.AssignmentDelegationSnapshot(1,null,7,false,null,until));
        var grant=new AssignmentDelegationResolver(client,standing).resolve(task,command,holding,decision);
        assertFalse(grant.delegated());
        org.mockito.Mockito.verify(standing).read(org.mockito.ArgumentMatchers.any());
    }
}
