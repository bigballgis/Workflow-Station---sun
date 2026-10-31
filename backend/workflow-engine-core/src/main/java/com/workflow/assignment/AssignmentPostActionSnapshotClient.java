package com.workflow.assignment;

import com.platform.common.assignment.*;
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
import java.util.Map;

@Component
public class AssignmentPostActionSnapshotClient {
    private final RestTemplate http;
    @Value("${user-portal.url:http://user-portal:8080}") private String baseUrl;
    @Value("${service.internal-token:}") private String serviceToken;
    public AssignmentPostActionSnapshotClient(@Qualifier(RestTemplateConfig.INTERNAL_API_REST_TEMPLATE) RestTemplate http) { this.http=http; }
    public Map<String,Object> read(AssignmentPostAction.SnapshotRequest request) {
        if (serviceToken.isBlank()) throw unavailable();
        try {
            var uri=SafeUrlInput.requireServiceOrigin(baseUrl).resolve("/api/portal/internal/assignment/post-action-snapshot");
            var headers=new HttpHeaders(); headers.set("X-Internal-Token",serviceToken);
            var response=http.exchange(uri,HttpMethod.POST,new HttpEntity<>(request,headers),
                    new ParameterizedTypeReference<ApiResponse<AssignmentPostAction.Snapshot>>() { }).getBody();
            var snapshot=response==null || !response.isSuccess()?null:response.getData();
            if (snapshot==null || !request.equals(snapshot.request()) || snapshot.variables()==null) throw unavailable();
            return snapshot.variables();
        } catch (RestClientException | IllegalArgumentException failure) { throw unavailable(); }
    }
    private AssignmentFailure unavailable() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE); }
}
