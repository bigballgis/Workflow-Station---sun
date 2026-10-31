package com.portal.client;

import com.platform.common.assignment.AssignmentFailure;
import com.platform.common.dto.ApiResponse;
import com.platform.common.util.JsonUtils;
import com.platform.common.util.SafeUrlInput;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;
import java.util.List;
import java.util.Map;

/** Shared service authentication for task effects and reopen recovery; snapshots always use a short timeout. */
@Component
public class AssignmentEngineTransport {
    private final RestTemplate shortHttp;
    private final RestTemplate workflowHttp;
    private final com.platform.common.assignment.AssignmentCoordinationVerifier coordination;
    @Value("${workflow-engine.url:http://localhost:8081}") private String origin;
    @Value("${service.internal-token:}") private String serviceToken;
    public AssignmentEngineTransport(@Qualifier("assignmentSnapshotRestTemplate") RestTemplate shortHttp,
            @Qualifier("restTemplate") RestTemplate workflowHttp,com.platform.common.assignment.AssignmentCoordinationVerifier coordination) {
        this.shortHttp=shortHttp; this.workflowHttp=workflowHttp; this.coordination=coordination;
    }

    public <T> T get(List<String> path, Map<String,?> query, ParameterizedTypeReference<ApiResponse<T>> type) {
        T result=call(shortHttp, endpoint(path, query), HttpMethod.GET, null, type);
        // Explicit absence/empty recovery queues contain no protected effect and must not impose its DB topology on legacy work.
        if (result!=null && (!(result instanceof java.util.Collection<?> rows) || !rows.isEmpty())) coordination.requireSamePrimary();
        return result;
    }
    public <T> T post(List<String> path, Object body, ParameterizedTypeReference<ApiResponse<T>> type, boolean executesWorkflow) {
        if (executesWorkflow || !path.isEmpty() && java.util.Set.of("operations","reopens","post-actions").contains(path.get(0))) {
            coordination.requireSamePrimary();
        }
        return call(executesWorkflow ? workflowHttp : shortHttp, endpoint(path, Map.of()), HttpMethod.POST, body, type);
    }
    private <T> T call(RestTemplate http, URI uri, HttpMethod method, Object body, ParameterizedTypeReference<ApiResponse<T>> type) {
        if (serviceToken.isBlank()) throw unavailable();
        var headers = new HttpHeaders(); headers.set("X-Internal-Token", serviceToken);
        try {
            var response = http.exchange(uri, method, new HttpEntity<>(body, headers), type).getBody();
            if (response == null || !response.isSuccess()) throw unavailable();
            return response.getData();
        } catch (RestClientResponseException response) { throw remoteFailure(response); }
        catch (RestClientException transport) { throw unavailable(); }
    }
    private URI endpoint(List<String> path, Map<String,?> query) {
        URI base;
        try { base = SafeUrlInput.requireServiceOrigin(origin); }
        catch (IllegalArgumentException failure) { throw unavailable(); }
        var builder = UriComponentsBuilder.fromUri(base).replacePath("").pathSegment("api", "v1", "internal", "assignment")
                .pathSegment(path.toArray(String[]::new));
        query.forEach((key,value) -> builder.queryParam(key, value));
        return builder.build().encode().toUri();
    }
    private AssignmentFailure remoteFailure(RestClientResponseException response) {
        if (response.getStatusCode().value() == 404) return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_ENGINE_UNSUPPORTED);
        try {
            var reason = JsonUtils.getObjectMapper().readTree(response.getResponseBodyAsString()).path("error").path("details").path("reason");
            if (reason.isTextual()) return new AssignmentFailure(AssignmentFailure.Reason.valueOf(reason.textValue()));
        } catch (java.io.IOException | IllegalArgumentException malformed) { throw unavailable(); }
        return unavailable();
    }
    private AssignmentFailure unavailable() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE); }
}
