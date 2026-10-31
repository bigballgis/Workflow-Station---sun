package com.portal.controller;

import com.platform.common.assignment.AssignmentServiceAuthentication;
import com.platform.common.assignment.AssignmentSourceSnapshot;
import com.platform.common.assignment.AssignmentSourceSnapshot.Request;
import com.platform.common.dto.ApiResponse;
import com.portal.component.AssignmentSourceSnapshotComponent;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/assignment")
public class AssignmentSourceSnapshotController {
    private final AssignmentSourceSnapshotComponent component;
    @Value("${service.internal-token:}") private String serviceToken;
    @org.springframework.beans.factory.annotation.Autowired @org.springframework.context.annotation.Lazy
    private com.platform.common.assignment.AssignmentCoordinationVerifier coordination;
    public AssignmentSourceSnapshotController(AssignmentSourceSnapshotComponent component) { this.component=component; }
    @PostMapping("/source-snapshot") public ApiResponse<AssignmentSourceSnapshot> read(@RequestHeader(value="X-Internal-Token",required=false) String token,
            @Valid @RequestBody AssignmentSourceSnapshot.Request request) {
        AssignmentServiceAuthentication.require(serviceToken,token);
        coordination.requireSamePrimary();
        return ApiResponse.success(component.read(request));
    }
    @PostMapping("/source-snapshots") public ApiResponse<java.util.List<AssignmentSourceSnapshot>> readMany(
            @RequestHeader(value="X-Internal-Token",required=false) String token,@Valid @RequestBody BatchRequest request) {
        AssignmentServiceAuthentication.require(serviceToken,token);
        coordination.requireSamePrimary();
        return ApiResponse.success(component.readMany(request.requests()));
    }
    public record BatchRequest(@jakarta.validation.constraints.NotEmpty @jakarta.validation.constraints.Size(max=1000)
            java.util.List<@jakarta.validation.constraints.NotNull @Valid Request> requests) { }
}
