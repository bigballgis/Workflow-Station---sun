package com.workflow.assignment;

import com.platform.common.assignment.AssignmentFailure;
import com.platform.common.dto.ApiResponse;
import com.platform.common.util.SafeUrlInput;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import java.util.Map;

/** Private, fail-closed transport selected only inside protected source evaluation. */
public final class AssignmentActiveCandidateTransport {
    private AssignmentActiveCandidateTransport() { }
    public static List<String> read(RestTemplate http,String base,String token,Map<String,String> request) {
        if(token==null || token.isBlank()) throw unavailable();
        var headers=new HttpHeaders(); headers.set("X-Internal-Token",token);
        try {
            var uri=SafeUrlInput.requireServiceOrigin(base).resolve("/api/v1/admin/internal/assignment-identities/candidates");
            var response=http.exchange(uri,HttpMethod.POST,new HttpEntity<>(request,headers),
                    new ParameterizedTypeReference<ApiResponse<List<String>>>() { }).getBody();
            if(response==null || !response.isSuccess() || response.getData()==null
                    || response.getData().stream().anyMatch(id->id==null || id.isBlank())) throw unavailable();
            return List.copyOf(response.getData());
        } catch(org.springframework.web.client.RestClientException | IllegalArgumentException failure) { throw unavailable(); }
    }
    private static AssignmentFailure unavailable() {
        return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE);
    }
}
