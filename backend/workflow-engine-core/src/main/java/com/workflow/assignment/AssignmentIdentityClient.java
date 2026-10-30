package com.workflow.assignment;

import com.platform.common.assignment.AssignmentIdentitySnapshot;
import com.platform.common.dto.ApiResponse;
import com.workflow.config.RestTemplateConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;

/** No display cache or fuzzy lookup is permitted in a runtime identity decision. */
@Component
public class AssignmentIdentityClient {
    private final RestTemplate http;
    @Value("${admin-center.url:http://localhost:8090}") private String baseUrl;
    @Value("${service.internal-token:}") private String serviceToken;

    public AssignmentIdentityClient(@Qualifier(RestTemplateConfig.INTERNAL_API_REST_TEMPLATE) RestTemplate http) {
        this.http = http;
    }

    public AssignmentIdentitySnapshot resolve(List<AssignmentIdentitySnapshot.Reference> references) {
        try { return resolveSnapshot(references); }
        catch (org.springframework.web.client.RestClientException | IllegalStateException | IllegalArgumentException failure) { throw unavailable(); }
    }

    private AssignmentIdentitySnapshot resolveSnapshot(List<AssignmentIdentitySnapshot.Reference> references) {
        if (references.isEmpty()) throw new IllegalArgumentException("Missing assignment references");
        if (serviceToken.isBlank()) throw new IllegalStateException("Assignment identity service authentication is not configured");
        URI base = com.platform.common.util.SafeUrlInput.requireServiceOrigin(baseUrl);
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", serviceToken);
        var request = new HttpEntity<>(new AssignmentIdentitySnapshot.Request(references), headers);
        var response = http.exchange(base.resolve("/api/v1/admin/internal/assignment-identities"), HttpMethod.POST, request,
                new ParameterizedTypeReference<ApiResponse<AssignmentIdentitySnapshot>>() { }).getBody();
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw new IllegalStateException("Assignment identity authority did not return a valid snapshot");
        }
        var snapshot = response.getData();
        var window=snapshot.timeWindow();
        if (window==null || window.observedAt()==null
                || window.previousBoundary()!=null && window.previousBoundary().isAfter(window.observedAt())
                || window.nextBoundary()!=null && !window.nextBoundary().isAfter(window.observedAt())) {
            throw new IllegalStateException("Assignment temporal authority window is unavailable or inconsistent");
        }
        if (snapshot.authorityRevision() < 0 || snapshot.users() == null || snapshot.missingReferences() == null) {
            throw new IllegalStateException("Invalid assignment authority version or missing-identity result");
        }
        var requested = references.stream().map(AssignmentIdentitySnapshot.Reference::value).collect(java.util.stream.Collectors.toSet());
        if (!requested.containsAll(snapshot.users().keySet()) || !requested.containsAll(snapshot.missingReferences())) {
            throw new IllegalStateException("Assignment identity response contains unrequested identities");
        }
        for (var reference : references) {
            var user = snapshot.users().get(reference.value());
            if (snapshot.missingReferences().contains(reference.value())) {
                if (user != null) throw new IllegalStateException("Contradictory assignment identity response");
                continue;
            }
            if (user == null || user.id() == null || user.id().isBlank() || user.username() == null
                    || reference.kind() == AssignmentIdentitySnapshot.Kind.ID && !reference.value().equals(user.id())
                    || reference.kind() == AssignmentIdentitySnapshot.Kind.USERNAME && !reference.value().equals(user.username())
                    || reference.kind() == AssignmentIdentitySnapshot.Kind.ID_OR_USERNAME
                        && !reference.value().equals(user.id()) && !reference.value().equals(user.username())) {
                throw new IllegalStateException("Assignment identity response does not match its exact request");
            }
        }
        return snapshot;
    }

    public com.platform.common.assignment.AssignmentManagementSnapshot management(String actor) {
        if (serviceToken.isBlank()) throw unavailable();
        var headers = new HttpHeaders(); headers.set("X-Internal-Token", serviceToken);
        try {
            var uri = com.platform.common.util.SafeUrlInput.requireServiceOrigin(baseUrl).resolve("/api/v1/admin/internal/assignment-identities/management");
            var request = new HttpEntity<>(new com.platform.common.assignment.AssignmentManagementSnapshot.Request(actor), headers);
            var response = http.exchange(uri, HttpMethod.POST, request,
                    new ParameterizedTypeReference<ApiResponse<com.platform.common.assignment.AssignmentManagementSnapshot>>() { }).getBody();
            if (response == null || !response.isSuccess() || response.getData() == null || !actor.equals(response.getData().userId())
                    || response.getData().authorityRevision() < 0) throw unavailable();
            return response.getData();
        } catch (org.springframework.web.client.RestClientException | IllegalArgumentException failure) { throw unavailable(); }
    }

    private com.platform.common.assignment.AssignmentFailure unavailable() {
        return new com.platform.common.assignment.AssignmentFailure(com.platform.common.assignment.AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE);
    }
}
