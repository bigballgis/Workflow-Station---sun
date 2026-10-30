package com.workflow.assignment;
import com.platform.common.assignment.*;

import com.platform.common.util.JsonUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import static com.platform.common.assignment.AssignmentCoordinationProof.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AssignmentCoordinationVerifierTest {
    @ParameterizedTest
    @ValueSource(strings={"null","{}","{\"success\":\"true\",\"data\":{}}","{\"success\":false,\"data\":{}}",
            "{\"success\":true,\"data\":null}","{\"success\":true,\"data\":42}"})
    void malformedChallengeCannotWriteLocalProofOrEstablishReadiness(String response) {
        var local=mock(AssignmentCoordinationProofRepository.class);
        var http=AssignmentPublicationCapability.newTransport();
        var server=MockRestServiceServer.bindTo(http).build();
        server.expect(request->{}).andRespond(withSuccess(response,MediaType.APPLICATION_JSON));
        var verifier=new AssignmentCoordinationVerifier(local,http,"http://engine","synthetic-proof-token",Participant.USER_PORTAL);
        unavailable(verifier::requireSamePrimary); unavailable(verifier::requireVerified);
        verifyNoInteractions(local); server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings={"\"true\"","1","{}","false","null"})
    void confirmationRequiresAnActualBooleanTrue(String confirmation) throws Exception {
        var local=mock(AssignmentCoordinationProofRepository.class);
        var http=AssignmentPublicationCapability.newTransport();
        var server=MockRestServiceServer.bindTo(http).build();
        var challenge=new Challenge(UUID.randomUUID(),UUID.randomUUID(),Participant.USER_PORTAL,Map.of("synthetic",1L),Instant.now().plusSeconds(30));
        String data=JsonUtils.getObjectMapper().writeValueAsString(challenge);
        server.expect(request->{}).andRespond(withSuccess("{\"success\":true,\"data\":"+data+"}",MediaType.APPLICATION_JSON));
        server.expect(request->{}).andRespond(withSuccess("{\"success\":true,\"data\":"+confirmation+"}",MediaType.APPLICATION_JSON));
        var verifier=new AssignmentCoordinationVerifier(local,http,"http://engine","synthetic-proof-token",Participant.USER_PORTAL);
        unavailable(verifier::requireSamePrimary); unavailable(verifier::requireVerified);
        verify(local).acknowledge(eq(challenge),any(UUID.class)); server.verify();
    }

    private void unavailable(org.junit.jupiter.api.function.Executable action) {
        assertEquals(AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE.name(),
                assertThrows(AssignmentFailure.class,action).getDetails().get("reason"));
    }
}
