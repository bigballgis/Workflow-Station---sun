package com.portal.controller;

import com.platform.common.assignment.AssignmentPostAction;
import com.platform.common.assignment.AssignmentServiceAuthentication;
import com.platform.common.dto.ApiResponse;
import com.portal.repository.AssignmentPostActionRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/assignment/post-action-snapshot")
public class AssignmentPostActionSnapshotController {
    private final AssignmentPostActionRepository repository;
    @Value("${service.internal-token:}") private String serviceToken;
    @org.springframework.beans.factory.annotation.Autowired @org.springframework.context.annotation.Lazy
    private com.platform.common.assignment.AssignmentCoordinationVerifier coordination;
    public AssignmentPostActionSnapshotController(AssignmentPostActionRepository repository) { this.repository=repository; }
    @PostMapping public ApiResponse<AssignmentPostAction.Snapshot> read(@RequestHeader(value="X-Internal-Token",required=false) String token,
            @Valid @RequestBody AssignmentPostAction.SnapshotRequest request) {
        AssignmentServiceAuthentication.require(serviceToken,token);
        coordination.requireSamePrimary();
        return ApiResponse.success(new AssignmentPostAction.Snapshot(request,repository.variables(request)));
    }
}
