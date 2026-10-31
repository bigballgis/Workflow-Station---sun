package com.workflow.controller;

import com.platform.common.assignment.*;
import com.platform.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import static com.platform.common.assignment.AssignmentCoordinationProof.*;

/** Operational proof is available while workflow readiness is false; no user workflow is started or modified. */
@RestController
@RequestMapping("/api/v1/internal/assignment/coordination-proofs")
public class AssignmentCoordinationController {
    private final AssignmentCoordinationProofRepository repository;
    @Value("${service.internal-token:}") private String token;
    public AssignmentCoordinationController(AssignmentCoordinationProofRepository repository) { this.repository=repository; }
    @PostMapping public ApiResponse<Challenge> create(
            @RequestHeader(value="X-Internal-Token",required=false) String supplied,@Valid @RequestBody Request request) {
        AssignmentServiceAuthentication.require(token,supplied);
        return ApiResponse.success(repository.create(request.participant()));
    }
    @PostMapping("/confirm") public ApiResponse<Boolean> confirm(
            @RequestHeader(value="X-Internal-Token",required=false) String supplied,@Valid @RequestBody Confirmation request) {
        AssignmentServiceAuthentication.require(token,supplied);
        return ApiResponse.success(repository.confirm(request));
    }
}
