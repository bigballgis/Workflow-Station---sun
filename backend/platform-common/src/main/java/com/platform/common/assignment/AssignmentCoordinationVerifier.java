package com.platform.common.assignment;

import com.fasterxml.jackson.databind.JsonNode;
import com.platform.common.util.JsonUtils;
import com.platform.common.util.SafeUrlInput;
import org.springframework.http.*;
import org.springframework.web.client.*;
import java.net.URI;
import java.util.UUID;
import static com.platform.common.assignment.AssignmentCoordinationProof.*;

/** Per application instance: a successful origin-witnessed local write proves the coordination domain before protected use. */
public class AssignmentCoordinationVerifier {
    private final AssignmentCoordinationProofRepository local;
    private final RestOperations http;
    private final URI endpoint;
    private final String token;
    private final Participant participant;
    private boolean verified;
    public AssignmentCoordinationVerifier(AssignmentCoordinationProofRepository local,RestOperations http,String origin,String token,Participant participant) {
        this.local=local; this.http=http; this.token=token; this.participant=participant;
        endpoint=SafeUrlInput.requireServiceOrigin(origin).resolve("/api/v1/internal/assignment/coordination-proofs");
    }
    public synchronized void requireSamePrimary() {
        if (verified) return;
        if (token==null || token.isBlank()) throw unavailable();
        Challenge challenge;
        try { challenge=JsonUtils.getObjectMapper().treeToValue(call(endpoint,new Request(participant)),Challenge.class); }
        catch (java.io.IOException invalid) { throw unavailable(); }
        if (challenge.probeId()==null || challenge.nonce()==null || challenge.participant()!=participant || challenge.expiresAt()==null) throw unavailable();
        var acknowledgement=UUID.randomUUID();
        local.acknowledge(challenge,acknowledgement); // Its neutral short transaction commits before the next HTTP call.
        var confirmed=call(endpoint.resolve(endpoint.getPath()+"/confirm"),
                new Confirmation(challenge.probeId(),challenge.nonce(),participant,acknowledgement));
        if (!confirmed.isBoolean() || !confirmed.booleanValue()) throw unavailable();
        verified=true;
    }
    /** Transactional consumers never initiate HTTP while acquiring their business fence. */
    public synchronized void requireVerified() { if (!verified) throw unavailable(); }
    private JsonNode call(URI uri,Object request) {
        var headers=new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON); headers.set("X-Internal-Token",token);
        try {
            var response=http.exchange(uri,HttpMethod.POST,new HttpEntity<>(request,headers),JsonNode.class);
            var body=response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || body==null || !body.isObject() || !body.path("success").isBoolean()
                    || !body.path("success").booleanValue() || body.path("data").isMissingNode() || body.path("data").isNull()) throw unavailable();
            return body.path("data");
        } catch (RestClientResponseException failure) {
            if (failure.getStatusCode().value()==404 || failure.getStatusCode().value()==501) {
                throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_ENGINE_UNSUPPORTED);
            }
            throw unavailable();
        } catch (RestClientException failure) { throw unavailable(); }
    }
    private AssignmentFailure unavailable() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE); }
}
