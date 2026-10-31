package com.workflow.controller;

import com.platform.common.assignment.AssignmentCompletionRequest;
import com.platform.common.assignment.AssignmentOperationReceipt;
import com.platform.common.dto.ApiResponse;
import com.workflow.assignment.AssignmentAcceptedCompletion;
import com.workflow.assignment.AssignmentOperationRecovery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Receipt payloads and recovery commands are service-only; operation IDs are never access credentials. */
@RestController
@RequestMapping("/api/v1/internal/assignment/operations")
public class AssignmentOperationController {
    private final AssignmentAcceptedCompletion completion;
    private final AssignmentOperationRecovery recovery;
    @Value("${service.internal-token:}") private String serviceToken;

    public AssignmentOperationController(AssignmentAcceptedCompletion completion, AssignmentOperationRecovery recovery) {
        this.completion = completion; this.recovery = recovery;
    }

    @PostMapping public ApiResponse<AssignmentOperationReceipt> complete(
            @RequestHeader(value="X-Internal-Token", required=false) String token, @Valid @RequestBody AssignmentCompletionRequest request) {
        authenticate(token);
        return ApiResponse.success(completion.complete(request));
    }

    @GetMapping("/{id}") public ApiResponse<AssignmentOperationReceipt> find(
            @RequestHeader(value="X-Internal-Token", required=false) String token, @PathVariable UUID id) {
        authenticate(token);
        return ApiResponse.success(recovery.find(id).orElse(null)); // An unknown, not-yet-accepted operation is explicit null.
    }

    @GetMapping("/by-task/{taskId}") public ApiResponse<AssignmentOperationReceipt> byTask(
            @RequestHeader(value="X-Internal-Token", required=false) String token, @PathVariable String taskId) {
        authenticate(token);
        return ApiResponse.success(recovery.byTask(taskId).orElse(null));
    }

    @GetMapping public ApiResponse<List<AssignmentOperationReceipt>> pending(
            @RequestHeader(value="X-Internal-Token", required=false) String token, @RequestParam(defaultValue="50") int limit) {
        authenticate(token);
        return ApiResponse.success(recovery.pending(limit));
    }

    @PostMapping("/{id}/confirm") public ApiResponse<Boolean> confirm(
            @RequestHeader(value="X-Internal-Token", required=false) String token, @PathVariable UUID id, @Valid @RequestBody Confirmation request) {
        authenticate(token);
        recovery.confirm(id, request.digest());
        return ApiResponse.success(true);
    }

    private void authenticate(String token) {
        com.platform.common.assignment.AssignmentServiceAuthentication.require(serviceToken, token);
    }
    @PostMapping("/{id}/retry") public ApiResponse<Boolean> retry(
            @RequestHeader(value="X-Internal-Token", required=false) String token, @PathVariable UUID id, @RequestBody Retry request) {
        authenticate(token);
        recovery.retry(id, request.conflict());
        return ApiResponse.success(true);
    }
    public record Retry(boolean conflict) { }
    public record Confirmation(@NotBlank @Pattern(regexp="[0-9a-f]{64}") String digest) { }
}
