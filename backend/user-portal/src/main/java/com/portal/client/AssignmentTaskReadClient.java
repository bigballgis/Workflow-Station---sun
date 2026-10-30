package com.portal.client;

import com.platform.common.assignment.AssignmentFailure;
import com.platform.common.assignment.AssignmentTaskReadSnapshot;
import com.platform.common.dto.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import java.util.List;

/** Short service-authenticated read; never a substitute for final operation authorization. */
@Component
public class AssignmentTaskReadClient {
    private static final ParameterizedTypeReference<ApiResponse<AssignmentTaskReadSnapshot>> TYPE = new ParameterizedTypeReference<>() { };
    private final AssignmentEngineTransport transport;
    public AssignmentTaskReadClient(AssignmentEngineTransport transport) { this.transport=transport; }
    public AssignmentTaskReadSnapshot read(List<String> tasks,String actor) {
        var snapshot=transport.post(List.of("task-reads"),new AssignmentTaskReadSnapshot.Request(tasks,actor),TYPE,false);
        if (snapshot==null) throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE);
        return snapshot;
    }
}
