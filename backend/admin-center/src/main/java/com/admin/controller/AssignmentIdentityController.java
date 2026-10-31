package com.admin.controller;

import com.admin.component.AssignmentIdentityComponent;
import com.platform.common.assignment.AssignmentIdentitySnapshot;
import com.platform.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/** Service-only exact resolver; a user-facing search endpoint is not a runtime identity authority. */
@RestController
@RequestMapping("/internal/assignment-identities")
public class AssignmentIdentityController {
    private final AssignmentIdentityComponent component;
    @Value("${service.internal-token:}") private String serviceToken;
    @org.springframework.beans.factory.annotation.Autowired @org.springframework.context.annotation.Lazy
    private com.platform.common.assignment.AssignmentCoordinationVerifier coordination;
    @org.springframework.beans.factory.annotation.Autowired
    private com.admin.component.AssignmentActiveCandidates candidates;

    public AssignmentIdentityController(AssignmentIdentityComponent component) {
        this.component = component;
    }

    @PostMapping
    public ApiResponse<AssignmentIdentitySnapshot> resolve(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @Valid @RequestBody AssignmentIdentitySnapshot.Request request) {
        com.platform.common.assignment.AssignmentServiceAuthentication.require(serviceToken, token);
        coordination.requireSamePrimary();
        return ApiResponse.success(component.resolve(request));
    }

    @PostMapping("/management")
    public ApiResponse<com.platform.common.assignment.AssignmentManagementSnapshot> management(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @Valid @RequestBody com.platform.common.assignment.AssignmentManagementSnapshot.Request request) {
        com.platform.common.assignment.AssignmentServiceAuthentication.require(serviceToken, token);
        coordination.requireSamePrimary();
        return ApiResponse.success(component.management(request.userId()));
    }

    @PostMapping("/candidates")
    public ApiResponse<java.util.List<String>> candidates(
            @RequestHeader(value="X-Internal-Token",required=false) String token,
            @Valid @RequestBody com.admin.component.AssignmentActiveCandidates.Request request) {
        com.platform.common.assignment.AssignmentServiceAuthentication.require(serviceToken,token);
        coordination.requireSamePrimary();
        return ApiResponse.success(candidates.resolve(request));
    }
}
