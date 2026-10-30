package com.portal.client;

import com.platform.common.assignment.*;
import com.platform.common.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AssignmentEngineTransportCoordinationTest {
    private final AssignmentCoordinationVerifier coordination=mock(AssignmentCoordinationVerifier.class);
    private final org.springframework.web.client.RestTemplate http=AssignmentPublicationCapability.newTransport();
    private final MockRestServiceServer server=MockRestServiceServer.bindTo(http).build();
    private final AssignmentEngineTransport transport=new AssignmentEngineTransport(http,http,coordination);
    private static final ParameterizedTypeReference<ApiResponse<String>> RECEIPT=new ParameterizedTypeReference<>() { };
    AssignmentEngineTransportCoordinationTest() {
        ReflectionTestUtils.setField(transport,"origin","http://engine");
        ReflectionTestUtils.setField(transport,"serviceToken","synthetic-transport-token");
    }
    @Test void explicitAbsentReceiptDoesNotImposeTheProtectedDatabaseRequirementOnLegacyCompletion() {
        doThrow(new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE)).when(coordination).requireSamePrimary();
        server.expect(request->{}).andRespond(withSuccess("{\"success\":true,\"data\":null}",MediaType.APPLICATION_JSON));
        assertNull(transport.get(List.of("operations","synthetic-id"),Map.of(),RECEIPT));
        verifyNoInteractions(coordination); server.verify();
    }
    @Test void emptyRecoveryQueueNeedsNoPhysicalProof() {
        server.expect(request->{}).andRespond(withSuccess("{\"success\":true,\"data\":[]}",MediaType.APPLICATION_JSON));
        var type=new ParameterizedTypeReference<ApiResponse<List<String>>>() { };
        assertEquals(List.of(),transport.get(List.of("reopens"),Map.of(),type));
        verifyNoInteractions(coordination); server.verify();
    }
    @Test void actualProtectedReceiptRequiresProofBeforeItCanReachTheMirror() {
        server.expect(request->{}).andRespond(withSuccess("{\"success\":true,\"data\":\"synthetic-receipt\"}",MediaType.APPLICATION_JSON));
        doThrow(new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE)).when(coordination).requireSamePrimary();
        assertThrows(AssignmentFailure.class,()->transport.get(List.of("operations","synthetic-id"),Map.of(),RECEIPT));
        verify(coordination).requireSamePrimary(); server.verify();
    }
}
