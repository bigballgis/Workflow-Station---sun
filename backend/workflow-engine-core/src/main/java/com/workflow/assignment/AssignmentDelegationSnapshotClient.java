package com.workflow.assignment;

import com.platform.common.assignment.AssignmentDelegationSnapshot;
import com.platform.common.assignment.AssignmentFailure;
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
public class AssignmentDelegationSnapshotClient {
    private final RestTemplate http;
    @Value("${user-portal.url:http://user-portal:8080}") private String baseUrl;
    @Value("${service.internal-token:}") private String serviceToken;
    public AssignmentDelegationSnapshotClient(@Qualifier(RestTemplateConfig.INTERNAL_API_REST_TEMPLATE) RestTemplate http) { this.http=http; }
    public AssignmentDelegationSnapshot read(AssignmentDelegationSnapshot.Request request) {
        if (serviceToken.isBlank()) throw unavailable();
        try {
            var uri=SafeUrlInput.requireServiceOrigin(baseUrl).resolve("/api/portal/internal/assignment/delegation-snapshot");
            var headers=new HttpHeaders(); headers.set("X-Internal-Token",serviceToken);
            var response=http.exchange(uri,HttpMethod.POST,new HttpEntity<>(request,headers),
                    new ParameterizedTypeReference<ApiResponse<AssignmentDelegationSnapshot>>() { }).getBody();
            var value=response==null || !response.isSuccess()?null:response.getData();
            if (value==null || value.contractVersion()!=1 || !request.equals(value.binding()) || value.authorityRevision()<0
                    || value.validUntil()==null || value.granted()!=(value.ruleId()!=null)
                    || value.ruleId()!=null && value.ruleId()<=0) throw unavailable();
            return value;
        } catch (RestClientException | IllegalArgumentException failure) { throw unavailable(); }
    }
    private AssignmentFailure unavailable() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE); }
}
