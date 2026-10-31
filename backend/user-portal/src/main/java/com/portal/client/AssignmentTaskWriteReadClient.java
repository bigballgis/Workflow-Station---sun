package com.portal.client;

import com.platform.common.assignment.*;
import com.platform.common.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.*;

/** Uses the existing authenticated-user permission endpoint with the short snapshot timeout. */
@Component
public class AssignmentTaskWriteReadClient {
    private final WorkflowEngineClient engine;
    private final RestTemplate http;
    public AssignmentTaskWriteReadClient(WorkflowEngineClient engine,@Qualifier("assignmentSnapshotRestTemplate") RestTemplate http) {
        this.engine=engine; this.http=http;
    }
    public AssignmentTaskWriteReadSnapshot read(String task,String actor) {
        try {
            var uri=UriComponentsBuilder.fromUri(SafeUrlInput.requireServiceOrigin(engine.engineUrl())).replacePath("")
                    .pathSegment("api","v1","tasks",SafeUrlInput.requirePathToken(task),"check-permission")
                    .queryParam("userId",actor).build().encode().toUri();
            var response=http.exchange(uri,HttpMethod.GET,engine.authorizedGetEntity(),
                    new ParameterizedTypeReference<Map<String,Object>>() { }).getBody();
            var data=ApiResponseBodyUnwrap.unwrapDataMap(response);
            if (!task.equals(data.get("taskId")) || !actor.equals(data.get("userId")) || !(data.get("hasPermission") instanceof Boolean)
                    || !(data.get("assignmentAccess") instanceof Map<?,?>)) throw unavailable();
            var access=JsonUtils.getObjectMapper().convertValue(data.get("assignmentAccess"),AssignmentTaskWriteReadSnapshot.class);
            if (access.contractVersion()!=1 || access.reason()==null) throw unavailable();
            if (access.exclusionAllowsWrite() && (!"CURRENT".equals(access.reason()) || !Boolean.TRUE.equals(access.tracked()) || access.stamp()==null
                    || access.stamp().caseRevision()==null || access.stamp().authorityRevision()<0 || access.stamp().validUntil()==null
                    || !access.stamp().validUntil().isAfter(java.time.Instant.now()))) throw unavailable();
            return access;
        } catch (RestClientException failure) { throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE,failure); }
        catch (IllegalArgumentException malformed) { throw unavailable(); }
    }
    private AssignmentFailure unavailable() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE); }
}
