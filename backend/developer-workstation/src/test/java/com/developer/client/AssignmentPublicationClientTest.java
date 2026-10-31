package com.developer.client;

import com.platform.common.assignment.AssignmentFailure;
import org.junit.jupiter.api.*;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** Actual service client and capability HTTP codec; deployment transport is isolated and counted. */
class AssignmentPublicationClientTest {
    RestTemplate workflow;
    WorkflowEngineClient client;
    MockRestServiceServer capabilities;
    private static final String XML="""
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"><process id="synthetic">
            <userTask id="review"><extensionElements><properties><property name="assignmentExclusion"
            value="{&quot;schemaVersion&quot;:1,&quot;excludeInitiator&quot;:true,&quot;excludePreviousHandlers&quot;:false,
            &quot;excludeSelectedNodeHandlers&quot;:false,&quot;selectedNodeKeys&quot;:[],&quot;backup&quot;:{&quot;enabled&quot;:false}}"/>
            </properties></extensionElements></userTask></process></definitions>
            """;
    @BeforeEach void configure() {
        workflow=mock(RestTemplate.class); client=new WorkflowEngineClient(workflow);
        ReflectionTestUtils.setField(client,"workflowEngineUrl","http://engine");
        ReflectionTestUtils.setField(client,"workflowEngineEnabled",true);
        ReflectionTestUtils.setField(client,"assignmentServiceToken","synthetic-publication-token");
        var transport=(RestTemplate)ReflectionTestUtils.getField(client,"assignmentCapabilityHttp");
        capabilities=MockRestServiceServer.bindTo(transport).build();
    }
    @Test void notReadyIsAnExplicitFailureBeforeHealthOrDeployment() {
        capability(false);
        var failure=assertThrows(AssignmentFailure.class,()->client.deployProcess("synthetic",XML,"Synthetic"));
        assertEquals("ASSIGNMENT_ENGINE_UNSUPPORTED",failure.getDetails().get("reason"));
        verifyNoInteractions(workflow); capabilities.verify();
    }
    @Test void missingCapabilityEndpointCannotFallBackToLegacyDeployment() {
        capabilities.expect(request->{}).andRespond(withStatus(HttpStatus.NOT_FOUND));
        var failure=assertThrows(AssignmentFailure.class,()->client.deployProcess("synthetic",XML,"Synthetic"));
        assertEquals("ASSIGNMENT_ENGINE_UNSUPPORTED",failure.getDetails().get("reason"));
        verifyNoInteractions(workflow); capabilities.verify();
    }
    @Test void anAffirmativeContractAllowsTheOriginalDeploymentAndKeepsItsExactIdentity() {
        capability(true); deployment();
        var result=client.deployProcess("synthetic",XML,"Synthetic").orElseThrow();
        assertInstanceOf(Map.class,result.get("data"));
        assertEquals("exact-id",((Map<?,?>)result.get("data")).get("engineProcessDefinitionId"));
        capabilities.verify(); verify(workflow).getForEntity(anyString(),eq(Map.class));
        verify(workflow).exchange(anyString(),eq(HttpMethod.POST),any(HttpEntity.class),any(ParameterizedTypeReference.class));
    }
    @Test void ordinaryLegacyPublicationDoesNotRequireACapabilityEndpointOrInternalToken() {
        ReflectionTestUtils.setField(client,"assignmentServiceToken","");
        deployment();
        assertTrue(client.deployProcess("synthetic","<bpmn/>","Synthetic legacy").isPresent());
        capabilities.verify();
        verify(workflow).exchange(anyString(),eq(HttpMethod.POST),any(HttpEntity.class),any(ParameterizedTypeReference.class));
    }
    private void capability(boolean ready) {
        capabilities.expect(request->{
            assertEquals("http://engine/api/v1/internal/assignment/capabilities",request.getURI().toString());
            assertEquals("synthetic-publication-token",request.getHeaders().getFirst("X-Internal-Token"));
        }).andRespond(withSuccess("{\"success\":true,\"data\":{\"contractVersion\":1,\"ready\":"+ready+"}}",MediaType.APPLICATION_JSON));
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private void deployment() {
        when(workflow.getForEntity(anyString(),eq(Map.class))).thenReturn(ResponseEntity.ok(Map.of("status","UP")));
        var data=Map.<String,Object>of("success",true,"deploymentId","owned-deployment","engineProcessDefinitionId","exact-id",
                "processDefinitionId","legacy-display-id","processDefinitionKey","synthetic","version",1);
        var response=ResponseEntity.ok(Map.<String,Object>of("success",true,"data",data));
        when(workflow.exchange(anyString(),eq(HttpMethod.POST),any(HttpEntity.class),any(ParameterizedTypeReference.class))).thenReturn(response);
    }
}
