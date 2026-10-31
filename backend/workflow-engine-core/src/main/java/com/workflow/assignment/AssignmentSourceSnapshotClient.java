package com.workflow.assignment;

import com.platform.common.assignment.AssignmentFailure;
import com.platform.common.assignment.AssignmentSourceSnapshot;
import com.platform.common.dto.ApiResponse;
import com.platform.common.util.SafeUrlInput;
import com.workflow.config.RestTemplateConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class AssignmentSourceSnapshotClient {
    private final RestTemplate http;
    @Value("${user-portal.url:http://user-portal:8080}") private String baseUrl;
    @Value("${service.internal-token:}") private String serviceToken;
    public AssignmentSourceSnapshotClient(@Qualifier(RestTemplateConfig.INTERNAL_API_REST_TEMPLATE) RestTemplate http) { this.http=http; }
    public AssignmentSourceSnapshot read(AssignmentSourceSnapshot.Request request) {
        if (serviceToken.isBlank()) throw unavailable();
        try {
            var uri = SafeUrlInput.requireServiceOrigin(baseUrl).resolve("/api/portal/internal/assignment/source-snapshot");
            var headers = new HttpHeaders(); headers.set("X-Internal-Token",serviceToken);
            var response = http.exchange(uri,HttpMethod.POST,new HttpEntity<>(request,headers),
                    new ParameterizedTypeReference<ApiResponse<AssignmentSourceSnapshot>>() { }).getBody();
            var value = response==null || !response.isSuccess() ? null : response.getData();
            return validate(request,value);
        } catch (RestClientException | IllegalArgumentException failure) { throw unavailable(); }
    }
    public java.util.List<AssignmentSourceSnapshot> readMany(java.util.List<AssignmentSourceSnapshot.Request> requests) {
        if (serviceToken.isBlank() || requests==null || requests.isEmpty() || requests.size()>1000) throw unavailable();
        try {
            var uri=SafeUrlInput.requireServiceOrigin(baseUrl).resolve("/api/portal/internal/assignment/source-snapshots");
            var headers=new HttpHeaders(); headers.set("X-Internal-Token",serviceToken);
            var response=http.exchange(uri,HttpMethod.POST,new HttpEntity<>(java.util.Map.of("requests",requests),headers),
                    new ParameterizedTypeReference<ApiResponse<java.util.List<AssignmentSourceSnapshot>>>() { }).getBody();
            var values=response==null || !response.isSuccess()?null:response.getData();
            if (values==null || values.size()!=requests.size()) throw unavailable();
            for (int index=0;index<requests.size();index++) validate(requests.get(index),values.get(index));
            return java.util.List.copyOf(values);
        } catch (RestClientException | IllegalArgumentException failure) { throw unavailable(); }
    }
    private AssignmentSourceSnapshot validate(AssignmentSourceSnapshot.Request request,AssignmentSourceSnapshot value) {
        if (value==null || value.contractVersion()!=1 || !request.caseId().equals(value.caseId())
                || !request.instanceId().equals(value.instanceId()) || value.caseRevision()<0 || value.portalRevision()<0
                || value.state()==null || value.values()==null || !value.values().isObject()) throw unavailable();
        var fields = value.values().fieldNames();
        while (fields.hasNext()) if (!request.fields().contains(fields.next())) throw unavailable();
        if (value.state()==AssignmentSourceSnapshot.State.INITIAL && !value.values().isEmpty()) throw unavailable();
        return value;
    }
    private AssignmentFailure unavailable() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE); }
}
