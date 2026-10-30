package com.platform.common.assignment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestOperations;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AssignmentPublicationCapabilityTest {
    private static final String TOKEN="synthetic-capability-token";
    private static final String XML="""
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"><process id="synthetic">
            <userTask id="review"><extensionElements><properties><property name="assignmentExclusion"
            value="{&quot;schemaVersion&quot;:1,&quot;excludeInitiator&quot;:true,&quot;excludePreviousHandlers&quot;:false,
            &quot;excludeSelectedNodeHandlers&quot;:false,&quot;selectedNodeKeys&quot;:[],&quot;backup&quot;:{&quot;enabled&quot;:false}}"/>
            </properties></extensionElements></userTask></process></definitions>
            """;

    @Test void affirmativeVersionOneUsesTheServiceAuthenticatedEndpoint() {
        var http=AssignmentPublicationCapability.newTransport(); var server=MockRestServiceServer.bindTo(http).build();
        server.expect(request->{
            assertEquals("http://engine/api/v1/internal/assignment/capabilities",request.getURI().toString());
            assertEquals(HttpMethod.GET,request.getMethod()); assertEquals(TOKEN,request.getHeaders().getFirst("X-Internal-Token"));
            assertNull(request.getHeaders().getFirst("Authorization"));
        }).andRespond(withSuccess("{\"success\":true,\"data\":{\"contractVersion\":1,\"ready\":true}}",MediaType.APPLICATION_JSON));
        AssignmentPublicationCapability.requireForPublication(http,"http://engine",TOKEN,XML,true); server.verify();
    }
    @ParameterizedTest
    @ValueSource(strings={"{\"contractVersion\":0,\"ready\":false}","{\"contractVersion\":1,\"ready\":false}",
            "{\"contractVersion\":2,\"ready\":true}","{\"contractVersion\":-1,\"ready\":true}"})
    void unavailableOrUnrecognizedContractIsNotTreatedAsSupported(String data) {
        rejected("{\"success\":true,\"data\":"+data+"}",AssignmentFailure.Reason.ASSIGNMENT_ENGINE_UNSUPPORTED);
    }
    @ParameterizedTest
    @ValueSource(strings={"null","{}","{\"success\":false,\"data\":{\"contractVersion\":1,\"ready\":true}}",
            "{\"success\":\"true\",\"data\":{\"contractVersion\":1,\"ready\":true}}",
            "{\"success\":true,\"data\":{\"contractVersion\":\"1\",\"ready\":true}}",
            "{\"success\":true,\"data\":{\"contractVersion\":1,\"ready\":\"true\"}}",
            "{\"success\":true,\"data\":{}}"})
    void malformedOrCoercedWireTypesCannotApprovePublication(String body) {
        rejected(body,AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE);
    }
    @ParameterizedTest @ValueSource(ints={401,403,404,500,501,503})
    void remoteStatusIsExplicitAndNeverLegacy(int status) {
        var http=AssignmentPublicationCapability.newTransport(); var server=MockRestServiceServer.bindTo(http).build();
        server.expect(request->{}).andRespond(withStatus(HttpStatus.valueOf(status)));
        var reason=status==404 || status==501?AssignmentFailure.Reason.ASSIGNMENT_ENGINE_UNSUPPORTED:AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE;
        failure(()->AssignmentPublicationCapability.requireForPublication(http,"http://engine",TOKEN,XML,true),reason); server.verify();
    }
    @Test void legacyNeedsNoNewEndpointOrServiceTokenAndDisabledProtectedEngineCannotIssueHttp() {
        var http=mock(RestOperations.class);
        AssignmentPublicationCapability.requireForPublication(http,"unused origin",null,"<bpmn/>",false);
        failure(()->AssignmentPublicationCapability.requireForPublication(http,"http://engine",TOKEN,XML,false),AssignmentFailure.Reason.ASSIGNMENT_ENGINE_UNSUPPORTED);
        failure(()->AssignmentPublicationCapability.requireForPublication(http,"http://engine","",XML,true),AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE);
        failure(()->AssignmentPublicationCapability.requireForPublication(http,"http://engine/extra",TOKEN,XML,true),AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE);
        verifyNoInteractions(http);
    }
    @Test void actualHttpRedirectIsNotFollowedAndDoesNotForwardTheServiceToken() throws Exception {
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        var redirected=new AtomicInteger(); var received=new AtomicReference<String>();
        server.createContext("/api/v1/internal/assignment/capabilities",exchange->{
            received.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            exchange.getResponseHeaders().set("Location","/forbidden"); exchange.sendResponseHeaders(302,-1); exchange.close();
        });
        server.createContext("/forbidden",exchange->{ redirected.incrementAndGet(); exchange.sendResponseHeaders(500,-1); exchange.close(); });
        server.start();
        try {
            failure(()->AssignmentPublicationCapability.requireForPublication(AssignmentPublicationCapability.newTransport(),
                    "http://127.0.0.1:"+server.getAddress().getPort(),TOKEN,XML,true),AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE);
            assertEquals(TOKEN,received.get()); assertEquals(0,redirected.get());
        } finally { server.stop(0); }
    }
    private void rejected(String body,AssignmentFailure.Reason reason) {
        var http=AssignmentPublicationCapability.newTransport(); var server=MockRestServiceServer.bindTo(http).build();
        server.expect(request->{}).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
        failure(()->AssignmentPublicationCapability.requireForPublication(http,"http://engine",TOKEN,XML,true),reason); server.verify();
    }
    private void failure(org.junit.jupiter.api.function.Executable action,AssignmentFailure.Reason reason) {
        assertEquals(reason.name(),assertThrows(AssignmentFailure.class,action).getDetails().get("reason"));
    }
}
