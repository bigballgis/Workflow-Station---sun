package com.platform.common.assignment;

import com.fasterxml.jackson.databind.JsonNode;
import com.platform.common.util.SafeUrlInput;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.*;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;

/** New portable policies require an affirmative, authenticated runtime contract; legacy XML needs no capability endpoint. */
public final class AssignmentPublicationCapability {
    private AssignmentPublicationCapability() { }
    public static RestTemplate newTransport() {
        var factory=new SimpleClientHttpRequestFactory() {
            @Override protected void prepareConnection(HttpURLConnection connection,String method) throws IOException {
                super.prepareConnection(connection,method); connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(2000); factory.setReadTimeout(3000);
        return new RestTemplate(factory);
    }
    public static boolean requireForPublication(RestOperations http,String origin,String token,String xml,boolean engineEnabled) {
        final boolean enabled;
        try { enabled=AssignmentExclusionBpmnValidator.policies(xml).values().stream().anyMatch(AssignmentExclusionPolicy::enabled); }
        catch (IllegalArgumentException invalid) { throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_POLICY_INVALID); }
        if (!enabled) return false;
        if (!engineEnabled) throw unsupported();
        if (token==null || token.isBlank()) throw unavailable();
        URI uri;
        try { uri=SafeUrlInput.requireServiceOrigin(origin).resolve("/api/v1/internal/assignment/capabilities"); }
        catch (IllegalArgumentException invalid) { throw unavailable(); }
        var headers=new HttpHeaders(); headers.set("X-Internal-Token",token);
        try {
            var response=http.exchange(uri,HttpMethod.GET,new HttpEntity<Void>(headers),JsonNode.class);
            if (!response.getStatusCode().is2xxSuccessful()) throw unavailable();
            validate(response.getBody());
            return true;
        } catch (RestClientResponseException failure) {
            if (failure.getStatusCode().value()==404 || failure.getStatusCode().value()==501) throw unsupported();
            throw unavailable();
        } catch (RestClientException failure) { throw unavailable(); }
    }
    private static void validate(JsonNode body) {
        if (body==null || !body.isObject() || !body.path("success").isBoolean() || !body.path("success").booleanValue()
                || !body.path("data").isObject()) throw unavailable();
        var data=body.path("data"); var version=data.path("contractVersion"); var ready=data.path("ready");
        if (!version.isIntegralNumber() || !version.canConvertToInt() || !ready.isBoolean()) throw unavailable();
        if (version.intValue()!=1 || !ready.booleanValue()) throw unsupported();
    }
    private static AssignmentFailure unsupported() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_ENGINE_UNSUPPORTED); }
    private static AssignmentFailure unavailable() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE); }
}
