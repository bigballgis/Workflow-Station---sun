package com.portal.controller;

import com.platform.common.assignment.AssignmentDelegationSnapshot;
import com.platform.common.assignment.AssignmentServiceAuthentication;
import com.platform.common.dto.ApiResponse;
import com.portal.component.AssignmentDelegationSnapshotComponent;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/assignment/delegation-snapshot")
public class AssignmentDelegationSnapshotController {
    private final AssignmentDelegationSnapshotComponent component;
    @Value("${service.internal-token:}") private String serviceToken;
    @org.springframework.beans.factory.annotation.Autowired @org.springframework.context.annotation.Lazy
    private com.platform.common.assignment.AssignmentCoordinationVerifier coordination;
    public AssignmentDelegationSnapshotController(AssignmentDelegationSnapshotComponent component) { this.component=component; }
    @PostMapping public ApiResponse<AssignmentDelegationSnapshot> read(@RequestHeader(value="X-Internal-Token",required=false) String token,
            @Valid @RequestBody AssignmentDelegationSnapshot.Request request) {
        AssignmentServiceAuthentication.require(serviceToken,token);
        coordination.requireSamePrimary();
        return ApiResponse.success(component.read(request));
    }
}
